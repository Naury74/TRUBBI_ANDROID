package com.naury.trubbi.domain.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.location.Fix
import com.naury.trubbi.core.location.LiveLocation
import com.naury.trubbi.core.notification.NotificationIcons
import com.naury.trubbi.core.voice.VoicePrompter
import com.naury.trubbi.data.AdvanceEvent
import com.naury.trubbi.data.JourneyControlSignal
import com.naury.trubbi.data.i18n.displayName
import com.naury.trubbi.data.i18n.routeLabel
import com.naury.trubbi.data.local.JourneyStopEntity
import com.naury.trubbi.data.local.JourneyWithStops
import com.naury.trubbi.data.repo.JourneyRepository
import com.naury.trubbi.data.settings.SettingsRepository
import com.naury.trubbi.domain.model.StopPhase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 여정이 진행되는 동안만 살아 있는 포그라운드 서비스.
 *
 * GPS 자취를 남겨 경로선과 주행 거리를 만들고, 다음 정차지를 보여 주는 상시 알림을 띄우며,
 * 도착 반경에 들어오면 알림과 음성으로 알린다.
 *
 * 좌표는 Fused(GPS·와이파이·센서 융합)로 받고, Play 서비스가 없으면 [LocationManager] 로 내려간다. 받은 좌표는
 * [FixFilter] 로 튀는 점을 걸러 낸 뒤 [SAVE_DIST_M] 만큼 움직였을 때만 저장한다. 도착은 [ArrivalDetector] 가
 * 머문 시간과 정확도까지 보고 판정한다. 여정 시작~완료 사이에만 동작하며, 시스템이 프로세스를 내렸다가 다시
 * 띄우면 진행 중인 여정을 찾아 이어서 기록한다.
 */
@AndroidEntryPoint
class JourneyTrackingService : Service() {

    @Inject lateinit var journeyRepo: JourneyRepository

    @Inject lateinit var settingsRepo: SettingsRepository

    @Inject lateinit var voice: VoicePrompter

    @Inject lateinit var controlSignal: JourneyControlSignal

    @Inject lateinit var liveLocation: LiveLocation

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 좌표 처리는 순서가 중요하다(직전 점과 비교). 한 번에 하나씩 돌도록 묶는다.
    private val fixLock = Mutex()
    private var journeyId: String? = null
    private var lastSaved: Fix? = null
    private var lastFix: Fix? = null
    private var latestJourney: JourneyWithStops? = null
    private var radiusM = DEFAULT_ARRIVAL_RADIUS_M
    private var voiceOn = true
    private var watchJob: Job? = null
    private var listener: LocationListener? = null
    private var fusedCallback: LocationCallback? = null
    private var filter = FixFilter()
    private val arrival = ArrivalDetector()

    /** 알림을 마지막으로 그린 때의 남은 거리. 좌표마다 다시 그리지 않고 의미 있게 바뀌었을 때만 그린다. */
    private var notifiedRemainingM: Float? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        scope.launch { settingsRepo.arrivalRadiusM.collect { radiusM = it } }
        scope.launch { settingsRepo.voiceGuide.collect { voiceOn = it } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_ARRIVE -> {
                ensureTracking()
                intent.getStringExtra(EXTRA_STOP_ID)?.let { stopId ->
                    scope.launch {
                        journeyRepo.arriveAtStop(stopId)
                        if (voiceOn) voice.speak(getString(R.string.voice_arrival_logged))
                    }
                }
                notificationManager().cancel(NOTIF_ID_ARRIVAL)
            }

            ACTION_ADVANCE -> {
                ensureTracking()
                advanceCurrent()
            }

            else -> {
                val id = intent?.getStringExtra(EXTRA_JOURNEY_ID)
                when {
                    id != null && id != journeyId -> if (!begin(id)) return START_NOT_STICKY
                    // 시스템이 프로세스를 내렸다가 START_STICKY 로 다시 띄우면 intent 가 없다. 진행 중인 여정을 찾아 잇는다.
                    id == null && journeyId == null -> ensureTracking()
                }
            }
        }
        return START_STICKY
    }

    /** 여정 [id] 의 추적을 처음부터 시작한다. 다른 여정을 추적 중이었으면 그 상태를 모두 비운다. */
    private fun begin(id: String): Boolean {
        stopTracking()
        if (!startForegroundSafely()) {
            stopSelf()
            return false
        }
        journeyId = id
        startLocationUpdates()
        watchJourney(id)
        return true
    }

    /**
     * 추적 중인 여정이 없으면 진행 중인 여정을 찾아 이어서 추적한다.
     *
     * 알림 버튼이나 재시작으로 서비스가 새로 떴을 때 여정을 모르면 버튼이 아무 일도 하지 않던 문제를 막는다.
     */
    private fun ensureTracking() {
        if (journeyId != null) return
        // 포그라운드 서비스는 시작 직후 곧바로 알림을 올려야 한다. 여정을 찾는 동안 기본 알림을 먼저 띄운다.
        if (!startForegroundSafely()) {
            stopSelf()
            return
        }
        scope.launch {
            val active = runCatching { journeyRepo.activeJourneyOnce() }.getOrNull()
            withContext(Dispatchers.Main) {
                when {
                    journeyId != null -> Unit
                    active == null -> stopSelf()
                    else -> {
                        latestJourney = active
                        begin(active.journey.journeyId)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        stopTracking()
        liveLocation.clear()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * 현재 정차지를 한 단계 진행한다. 상단바 액션과 플로팅 컨트롤이 공유하는 진입점이다.
     *
     * 앱 화면이 떠 있으면 음성은 앱이 담당하므로 여기서는 말하지 않는다(중복 방지).
     */
    private fun advanceCurrent() {
        scope.launch {
            val journey = latestJourney ?: return@launch
            val stop = journey.currentStop ?: return@launch
            val isLastStop = journey.orderedStops.count { it.completedAt == null } == 1
            val recorded = journeyRepo.advanceStop(stop)

            controlSignal.post(
                AdvanceEvent(stop.displayName(this@JourneyTrackingService), stop.kindEnum.label(this@JourneyTrackingService), recorded, isLastStop),
            )

            if (voiceOn && !appVisible()) {
                val kind = stop.kindEnum.label(this@JourneyTrackingService)
                when (recorded) {
                    StopPhase.WORKING -> voice.speak(getString(R.string.voice_work_started, kind))
                    StopPhase.DONE -> voice.speak(getString(R.string.voice_work_done, kind))
                    else -> Unit
                }
            }
        }
    }

    /** 앱이 화면에 보이는 중인지 — 보이면 음성과 도착 물음을 앱 쪽에 양보한다. */
    private fun appVisible(): Boolean = controlSignal.appForeground.value

    // ── 수명주기 ──────────────────────────────────────────────────────────────

    private fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission(): Boolean =
        hasFineLocation() ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** 안드로이드 14+ 는 위치 권한 없이 location 타입 FGS 를 거부하므로 미리 막는다. */
    private fun startForegroundSafely(): Boolean {
        if (!hasLocationPermission()) return false
        return runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID_ONGOING,
                buildOngoingNotification(null),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        }.isSuccess
    }

    /** 어디에서든 여정이 끝나면 서비스도 함께 내린다. */
    private fun watchJourney(id: String) {
        watchJob?.cancel()
        watchJob = scope.launch {
            journeyRepo.observeJourney(id).collect { journey ->
                // 다른 여정으로 바뀐 뒤 늦게 온 소식이면 무시한다(새 여정 추적까지 꺼 버리던 문제).
                if (id != journeyId) return@collect
                if (journey == null || !journey.journey.isActive) {
                    withContext(Dispatchers.Main) {
                        stopTracking()
                        stopSelf()
                    }
                } else {
                    latestJourney = journey
                    notifyOngoing(journey)
                }
            }
        }
    }

    /**
     * 위치 갱신을 시작한다.
     *
     * 정밀 위치 권한이 없으면(대략적 위치만 허용) GPS 를 요청할 수 없으므로 BALANCED 로 낮춘다. 예전에는 이때
     * GPS 를 요청하다 보안 예외가 나고 그대로 삼켜져 경로가 한 점도 남지 않았다.
     */
    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        val fine = hasFineLocation()
        // 대략적 위치는 반경이 수백 m~수 km 라 정밀 기준(50m)으로는 모두 버려진다. 거친 경로라도 남긴다.
        filter = FixFilter(maxAccuracyM = if (fine) MAX_ACCURACY_M else COARSE_MAX_ACCURACY_M)
        val request = LocationRequest.Builder(if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVAL_MS)
            .setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            .setMinUpdateDistanceMeters(MIN_DIST_M)
            .setWaitForAccurateLocation(fine)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach(::onFix)
            }
        }
        fusedCallback = callback
        val started = runCatching {
            LocationServices.getFusedLocationProviderClient(this)
                .requestLocationUpdates(request, callback, mainLooper)
                .addOnFailureListener {
                    fusedCallback = null
                    startLegacyUpdates(fine)
                }
        }.isSuccess
        if (!started) {
            fusedCallback = null
            startLegacyUpdates(fine)
        }
    }

    /** Play 서비스가 없는 기기용. GPS 와 네트워크를 둘 다 듣고 필터가 정확한 쪽을 고르게 한다. */
    @Suppress("MissingPermission")
    private fun startLegacyUpdates(fine: Boolean) {
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val newListener = LocationListener { onFix(it) }
        listener = newListener
        val providers = buildList {
            if (fine) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }
        providers.forEach { p ->
            runCatching { manager.requestLocationUpdates(p, INTERVAL_MS, MIN_DIST_M, newListener, mainLooper) }
        }
    }

    private fun stopTracking() {
        fusedCallback?.let { cb -> runCatching { LocationServices.getFusedLocationProviderClient(this).removeLocationUpdates(cb) } }
        fusedCallback = null
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        listener?.let { runCatching { manager?.removeUpdates(it) } }
        listener = null
        watchJob?.cancel()
        watchJob = null
        journeyId = null
        latestJourney = null
        // 이전 여정의 마지막 점과 새 여정 첫 점 사이 거리가 주행 거리에 더해지지 않도록 비운다.
        lastSaved = null
        lastFix = null
        notifiedRemainingM = null
        filter.reset()
        arrival.reset()
    }

    // ── 위치 갱신마다 하는 일 ─────────────────────────────────────────────────

    private fun onFix(location: Location) {
        val id = journeyId ?: return
        val fix = Fix(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyM = location.accuracy.takeIf { location.hasAccuracy() },
            timeMs = location.time,
            speedMps = location.speed.takeIf { location.hasSpeed() },
            bearing = location.bearing.takeIf { location.hasBearing() },
        )
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        scope.launch {
            fixLock.withLock {
                if (id != journeyId || !filter.accept(fix, ageMs)) return@withLock
                lastFix = fix
                liveLocation.publish(fix)
                val previous = lastSaved
                val moved = previous?.let { distanceM(it, fix) }
                if (moved == null || moved >= SAVE_DIST_M) {
                    journeyRepo.addRoutePoint(
                        journeyId = id,
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        speedKmh = fix.speedMps?.times(3.6f),
                        deltaM = moved?.toLong() ?: 0L,
                    )
                    lastSaved = fix
                }
                checkProximity(fix)
                latestJourney?.let { journey -> if (remainingChanged(journey)) notifyOngoing(journey) }
            }
        }
    }

    /** 남은 거리가 [NOTIFY_STEP_M] 이상 바뀌었는지. 좌표마다 알림을 다시 그리면 배터리를 쓰고 깜박인다. */
    private fun remainingChanged(journey: JourneyWithStops): Boolean {
        val now = journey.currentStop?.let(::remainingMeters) ?: return false
        val before = notifiedRemainingM
        return before == null || kotlin.math.abs(before - now) >= NOTIFY_STEP_M
    }

    private fun notifyOngoing(journey: JourneyWithStops) {
        notifiedRemainingM = journey.currentStop?.let(::remainingMeters)
        notificationManager().notify(NOTIF_ID_ONGOING, buildOngoingNotification(journey))
    }

    /**
     * 2단 안내 — 멀리서 한 번 "곧 도착", 반경 안에서 한 번 "도착 알림".
     *
     * 예고를 따로 두는 이유: 반경(기본 300m)에서 알리면 큰 물류센터에서는 이미 진입로를
     * 지나친 뒤라 늦다.
     */
    private fun checkProximity(fix: Fix) {
        val stop = latestJourney?.currentStop ?: return
        if (stop.arrivedAt != null) return
        val lat = stop.latitude ?: return
        val lng = stop.longitude ?: return
        val distance = distanceM(fix.latitude, fix.longitude, lat, lng)
        when (arrival.update(stop.stopId, distance, radiusM, fix)) {
            ArrivalDetector.Event.APPROACHING -> if (voiceOn) {
                voice.speak(getString(R.string.voice_approaching, stop.displayName(this), formatDistance(distance)))
            }
            ArrivalDetector.Event.ARRIVED -> {
                notifyArrival(stop)
                if (voiceOn) voice.speak(getString(R.string.voice_near_stop, stop.displayName(this)))
            }
            ArrivalDetector.Event.NONE -> Unit
        }
    }

    // ── 알림 ──────────────────────────────────────────────────────────────────

    private fun notificationManager() =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun createChannels() {
        val manager = notificationManager()
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING,
                getString(R.string.notif_channel_journey_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notif_channel_journey_desc)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                getString(R.string.notif_channel_arrival_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.notif_channel_arrival_desc)
            },
        )
    }

    private fun deepLinkIntent(requestCode: Int, uri: String): PendingIntent {
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            .setPackage(packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            this,
            requestCode,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun advancePendingIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            REQ_ADVANCE,
            Intent(this, JourneyTrackingService::class.java).setAction(ACTION_ADVANCE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun buildOngoingNotification(journey: JourneyWithStops?): Notification {
        val next = journey?.currentStop
        val text = when {
            journey == null -> getString(R.string.notif_journey_recording)
            next == null -> getString(R.string.notif_journey_all_done)
            else -> buildString {
                append(getString(R.string.notif_journey_next, next.displayName(this@JourneyTrackingService)))
                remainingDistance(next)?.let { append(" · $it") }
            }
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setLargeIcon(NotificationIcons.glyph(this, R.drawable.ic_stat_truck))
            .setColor(BRAND_COLOR)
            .setContentTitle(journey?.routeLabel(this) ?: getString(R.string.notif_journey_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(deepLinkIntent(REQ_OPEN_JOURNEY, "trubbi://journey"))
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)

        next?.let { stop ->
            val kind = stop.kindEnum.label(this)
            val actionLabel = when (stop.phase) {
                StopPhase.MOVING -> getString(R.string.notif_action_arrive)
                StopPhase.WAITING -> getString(R.string.action_start_kind, kind)
                StopPhase.WORKING -> getString(R.string.action_finish_kind, kind)
                StopPhase.DONE -> null
            }
            actionLabel?.let { builder.addAction(0, it, advancePendingIntent()) }
        }
        // journey=1 딥링크라 여기서 찍은 사진은 여정에 자동으로 귀속된다.
        builder.addAction(
            0,
            getString(R.string.notif_action_capture),
            deepLinkIntent(REQ_CAPTURE, "trubbi://capture?journey=1"),
        )
        return builder.build()
    }

    private fun remainingDistance(stop: JourneyStopEntity): String? = remainingMeters(stop)?.let(::formatDistance)

    private fun remainingMeters(stop: JourneyStopEntity): Float? {
        val fix = lastFix ?: return null
        val lat = stop.latitude ?: return null
        val lng = stop.longitude ?: return null
        return distanceM(fix.latitude, fix.longitude, lat, lng)
    }

    private fun formatDistance(meters: Float): String =
        if (meters >= 1000) "%.1fkm".format(meters / 1000f) else "${meters.toInt()}m"

    private fun notifyArrival(stop: JourneyStopEntity) {
        val arrive = PendingIntent.getService(
            this,
            stop.stopId.hashCode(),
            Intent(this, JourneyTrackingService::class.java).apply {
                action = ACTION_ARRIVE
                putExtra(EXTRA_STOP_ID, stop.stopId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setLargeIcon(NotificationIcons.glyph(this, R.drawable.ic_stat_pin))
            .setColor(BRAND_COLOR)
            .setContentTitle(getString(R.string.notif_arrival_title, stop.displayName(this)))
            .setContentText(getString(R.string.notif_arrival_text, stop.kindEnum.label(this)))
            .setAutoCancel(true)
            .setContentIntent(deepLinkIntent(REQ_OPEN_JOURNEY, "trubbi://journey"))
            .addAction(0, getString(R.string.notif_action_log_arrival), arrive)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        notificationManager().notify(NOTIF_ID_ARRIVAL, notification)
    }

    companion object {
        private const val CHANNEL_ONGOING = "journey_tracking"
        private const val CHANNEL_ALERT = "journey_arrival"
        private const val NOTIF_ID_ONGOING = 41
        private const val NOTIF_ID_ARRIVAL = 42

        private const val REQ_OPEN_JOURNEY = 0
        private const val REQ_CAPTURE = 1
        private const val REQ_ADVANCE = 2

        private const val DEFAULT_ARRIVAL_RADIUS_M = 300

        /** 위치를 받는 간격. 시속 90km 에서 10초면 250m 라 경로선이 도로를 따라 그려진다. */
        private const val INTERVAL_MS = 10_000L
        private const val FASTEST_INTERVAL_MS = 5_000L
        private const val MIN_DIST_M = 25f

        /** 이만큼 움직였을 때만 자취를 저장해 DB 행 수를 줄인다. */
        private const val SAVE_DIST_M = 60f

        /** 정밀 위치에서 이보다 반경이 큰 좌표는 경로에 넣지 않는다. */
        private const val MAX_ACCURACY_M = 50f

        /** 대략적 위치만 허용됐을 때의 기준. */
        private const val COARSE_MAX_ACCURACY_M = 2_000f

        /** 남은 거리가 이만큼 바뀌었을 때만 상시 알림을 다시 그린다. */
        private const val NOTIFY_STEP_M = 50f

        private val BRAND_COLOR = com.naury.trubbi.core.ui.BrandPalette.PRIMARY

        const val ACTION_STOP = "com.naury.trubbi.tracking.STOP"
        const val ACTION_ARRIVE = "com.naury.trubbi.tracking.ARRIVE"
        const val ACTION_ADVANCE = "com.naury.trubbi.tracking.ADVANCE"
        const val EXTRA_JOURNEY_ID = "journeyId"
        const val EXTRA_STOP_ID = "stopId"
    }
}
