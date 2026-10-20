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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
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
import com.naury.trubbi.domain.model.JourneyEventType
import com.naury.trubbi.domain.model.StopPhase
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 여정이 진행되는 동안만 살아 있는 포그라운드 서비스.
 *
 * GPS 자취를 남겨 경로선과 주행 거리를 만들고, 다음 정차지를 보여 주는 상시 알림을 띄우며,
 * 도착 반경에 들어오면 알림과 음성으로 알린다.
 *
 * 배터리를 아끼려고 위치 요청 자체를 [MIN_TIME_MS]/[MIN_DIST_M] 로 성기게 잡고, 저장은
 * 다시 [SAVE_DIST_M] 만큼 움직였을 때만 한다. 여정 시작~완료 사이에만 동작한다.
 */
@AndroidEntryPoint
class JourneyTrackingService : Service() {

    @Inject lateinit var journeyRepo: JourneyRepository

    @Inject lateinit var settingsRepo: SettingsRepository

    @Inject lateinit var voice: VoicePrompter

    @Inject lateinit var controlSignal: JourneyControlSignal

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var journeyId: String? = null
    private var lastSaved: Location? = null
    private var lastFix: Location? = null
    private var latestJourney: JourneyWithStops? = null
    private var radiusM = DEFAULT_ARRIVAL_RADIUS_M
    private var voiceOn = true
    private var listener: LocationListener? = null

    /** 이미 도착 알림을 보낸 정차지 — 한 곳당 한 번만 알린다. */
    private val alerted = mutableSetOf<String>()

    /** 이미 "곧 도착" 예고를 보낸 정차지. */
    private val approaching = mutableSetOf<String>()

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
                intent.getStringExtra(EXTRA_STOP_ID)?.let { stopId ->
                    scope.launch {
                        journeyRepo.arriveAtStop(stopId)
                        if (voiceOn) voice.speak(getString(R.string.voice_arrival_logged))
                    }
                }
                notificationManager().cancel(NOTIF_ID_ARRIVAL)
            }

            ACTION_ADVANCE -> advanceCurrent()

            else -> {
                val id = intent?.getStringExtra(EXTRA_JOURNEY_ID)
                if (id != null && id != journeyId) {
                    journeyId = id
                    if (!startForegroundSafely()) {
                        stopSelf()
                        return START_NOT_STICKY
                    }
                    startLocationUpdates()
                    watchJourney(id)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopTracking()
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

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
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
        scope.launch {
            journeyRepo.observeJourney(id).collect { journey ->
                if (journey == null || !journey.journey.isActive) {
                    stopTracking()
                    stopSelf()
                } else {
                    latestJourney = journey
                    notificationManager().notify(NOTIF_ID_ONGOING, buildOngoingNotification(journey))
                }
            }
        }
    }

    @Suppress("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        val provider = when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> return
        }
        val newListener = LocationListener { onFix(it) }
        listener = newListener
        runCatching { manager.requestLocationUpdates(provider, MIN_TIME_MS, MIN_DIST_M, newListener, mainLooper) }
    }

    private fun stopTracking() {
        val manager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        listener?.let { runCatching { manager?.removeUpdates(it) } }
        listener = null
        journeyId = null
        latestJourney = null
    }

    // ── 위치 갱신마다 하는 일 ─────────────────────────────────────────────────

    private fun onFix(fix: Location) {
        lastFix = fix
        val id = journeyId ?: return
        scope.launch {
            val previous = lastSaved
            val moved = previous?.distanceTo(fix)
            if (previous == null || moved == null || moved >= SAVE_DIST_M) {
                journeyRepo.addRoutePoint(
                    journeyId = id,
                    latitude = fix.latitude,
                    longitude = fix.longitude,
                    speedKmh = if (fix.hasSpeed()) fix.speed * 3.6f else null,
                    deltaM = moved?.toLong() ?: 0L,
                )
                lastSaved = fix
            }
            checkProximity(fix)
            latestJourney?.let { notificationManager().notify(NOTIF_ID_ONGOING, buildOngoingNotification(it)) }
        }
    }

    /**
     * 2단 안내 — 멀리서 한 번 "곧 도착", 반경 안에서 한 번 "도착 알림".
     *
     * 예고를 따로 두는 이유: 반경(기본 300m)에서 알리면 큰 물류센터에서는 이미 진입로를
     * 지나친 뒤라 늦다.
     */
    private fun checkProximity(fix: Location) {
        val stop = latestJourney?.currentStop ?: return
        if (stop.arrivedAt != null) return
        val lat = stop.latitude ?: return
        val lng = stop.longitude ?: return

        val out = FloatArray(1)
        Location.distanceBetween(fix.latitude, fix.longitude, lat, lng, out)
        val distance = out[0]

        val headsUpM = (radiusM * 4).coerceAtLeast(1000)
        if (distance <= headsUpM && stop.stopId !in approaching && stop.stopId !in alerted) {
            approaching += stop.stopId
            if (voiceOn) {
                voice.speak(getString(R.string.voice_approaching, stop.displayName(this), formatDistance(distance)))
            }
        }

        if (distance <= radiusM && stop.stopId !in alerted) {
            alerted += stop.stopId
            notifyArrival(stop)
            if (voiceOn) voice.speak(getString(R.string.voice_near_stop, stop.displayName(this)))
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
                StopPhase.WAITING -> getString(JourneyEventType.WORK_STARTED.titleRes, kind)
                StopPhase.WORKING -> getString(JourneyEventType.STOP_COMPLETED.titleRes, kind)
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

    private fun remainingDistance(stop: JourneyStopEntity): String? {
        val fix = lastFix ?: return null
        val lat = stop.latitude ?: return null
        val lng = stop.longitude ?: return null
        val out = FloatArray(1)
        Location.distanceBetween(fix.latitude, fix.longitude, lat, lng, out)
        return formatDistance(out[0])
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
        private const val MIN_TIME_MS = 20_000L
        private const val MIN_DIST_M = 60f

        /** 이만큼 움직였을 때만 자취를 저장해 DB 행 수를 줄인다. */
        private const val SAVE_DIST_M = 90f

        private val BRAND_COLOR = com.naury.trubbi.core.ui.BrandPalette.PRIMARY

        const val ACTION_STOP = "com.naury.trubbi.tracking.STOP"
        const val ACTION_ARRIVE = "com.naury.trubbi.tracking.ARRIVE"
        const val ACTION_ADVANCE = "com.naury.trubbi.tracking.ADVANCE"
        const val EXTRA_JOURNEY_ID = "journeyId"
        const val EXTRA_STOP_ID = "stopId"
    }
}
