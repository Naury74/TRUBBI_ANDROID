package com.naury.trubbi.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.naury.trubbi.MainActivity
import com.naury.trubbi.R
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.notification.NotificationIcons
import com.naury.trubbi.data.i18n.displayName
import com.naury.trubbi.data.i18n.routeLabel
import com.naury.trubbi.data.local.JourneyWithStops
import com.naury.trubbi.data.repo.JourneyRepository
import com.naury.trubbi.data.settings.SettingsRepository
import com.naury.trubbi.domain.model.JourneyEventType
import com.naury.trubbi.domain.model.StopPhase
import com.naury.trubbi.domain.tracking.JourneyTrackingService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 다른 앱 위에 떠 있는 빠른 실행 버블.
 *
 * 버블을 누르면 촬영·문서·바코드·메모·앱 열기·끄기 카드가 내려오고, 각각 딥링크로 바로 들어간다.
 * 여정 컨트롤 설정이 켜져 있고 진행 중인 여정이 있으면 "다음 단계" 행이 위에 붙는다.
 * 드래그해 좌우 가장자리에 붙여 둘 수 있고, 평소에는 손잡이만 내놓고 숨는다.
 *
 * 앱을 열지 않고 다른 앱 위에서 바로 기록을 남길 수 있다. 오버레이 권한이 필요하다.
 */
@AndroidEntryPoint
class FloatingLauncherService : Service() {

    @Inject lateinit var settingsRepo: SettingsRepository

    @Inject lateinit var journeyRepo: JourneyRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var wm: WindowManager? = null
    private var root: LinearLayout? = null
    private var panel: LinearLayout? = null
    private var bubbleView: View? = null
    private var journeyRow: View? = null
    private var journeyLabelView: TextView? = null
    private var journeyHeader: LinearLayout? = null
    private var journeyRouteView: TextView? = null
    private var journeyStageView: TextView? = null
    private var activeJourney: JourneyWithStops? = null
    private var journeyControlOn = false
    private var lastStageSig: String? = null
    private var expanded = false
    private var posY = 220
    private var dockedRight = true
    private val cardWidthDp = 220
    private var xAnimator: android.animation.ValueAnimator? = null
    private lateinit var params: WindowManager.LayoutParams

    private val bubbleSizePx get() = dp(56)

    /** 가장자리 밖으로 밀어 넣을 양 — 손잡이만 남긴다. */
    private fun peekPx() = (bubbleSizePx * 0.46f).toInt()
    private fun marginPx() = dp(6)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotice()
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        runCatching { addBubble() }.onFailure { stopSelf() }
        // 설정과 진행 중 여정을 함께 보고 '다음 단계' 행을 켜고 끈다.
        scope.launch {
            settingsRepo.floatingControl.collect {
                journeyControlOn = it
                updateJourneyRow()
            }
        }
        scope.launch {
            journeyRepo.activeJourney.collect { j ->
                activeJourney = j
                updateJourneyRow()
                // 단계가 바뀌면(도착·작업·완료) 알아서 펼쳐 다음에 누를 버튼을 눈앞에 둔다.
                val stageSignature = j?.let { "${it.currentStop?.stopId}:${it.currentStop?.phase}:${it.orderedStops.size}" }
                val changed = stageSignature != null && lastStageSig != null && stageSignature != lastStageSig
                if (journeyControlOn && changed && !expanded) expand()
                lastStageSig = stageSignature
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        runCatching { root?.let { wm?.removeView(it) } }
        root = null
        scope.cancel()
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ── 오버레이 UI ───────────────────────────────────────────────────────────

    private fun addBubble() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        this.wm = wm

        // 앱 아이콘의 어댑티브 드로어블을 그대로 그리면 마스킹이 어긋나 마스코트가 잘린다.
        // 크림색 원 + FIT_CENTER 마스코트로 직접 조립한다.
        val bubbleSize = bubbleSizePx
        val bubble = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFEEF2FF.toInt())
            }
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
            elevation = dp(14).toFloat()
            val mascot = ImageView(this@FloatingLauncherService).apply {
                setImageResource(R.drawable.brand_mark)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            val pad = dp(12)
            addView(
                mascot,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ).apply { setMargins(pad, pad, pad, pad) },
            )
        }
        bubbleView = bubble

        // 드롭다운 카드 메뉴.
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(0xF21E1E25.toInt())
            }
            elevation = dp(18).toFloat()
            setPadding(dp(6), dp(8), dp(6), dp(8))
        }
        card.addView(menuTitle(getString(R.string.floating_menu_title)))

        // 진행 중인 여정 요약 — 여정 컨트롤이 켜졌을 때만 보인다.
        val jHeader = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(16), dp(2), dp(16), dp(8))
        }
        val routeTv = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
        }
        val stageTv = TextView(this).apply {
            setTextColor(0xFFA9BEFF.toInt())
            textSize = 12.5f
        }
        jHeader.addView(routeTv)
        jHeader.addView(stageTv)
        journeyHeader = jHeader
        journeyRouteView = routeTv
        journeyStageView = stageTv
        card.addView(jHeader)

        val jRow =
            actionRow(R.drawable.ic_stat_truck, getString(R.string.floating_next_step), com.naury.trubbi.core.ui.BrandPalette.ACCENT) { onJourneyAction() }
        jRow.visibility = View.GONE
        journeyRow = jRow
        journeyLabelView = jRow.findViewWithTag<TextView>("label")
        card.addView(jRow)

        card.addView(
            actionRow(R.drawable.ic_widget_camera, getString(R.string.floating_capture), com.naury.trubbi.core.ui.BrandPalette.PRIMARY) {
                launch("trubbi://capture?purpose=ETC")
            },
        )
        card.addView(
            actionRow(R.drawable.ic_widget_document, getString(R.string.floating_scan_document), 0xFF2D6CDF.toInt()) {
                launch("trubbi://capture?purpose=ETC&mode=document")
            },
        )
        card.addView(
            actionRow(R.drawable.ic_widget_barcode, getString(R.string.floating_barcode), 0xFF2FB36B.toInt()) {
                launch("trubbi://scan?purpose=WAYBILL")
            },
        )
        card.addView(actionRow(R.drawable.ic_widget_memo, getString(R.string.floating_memo), 0xFF8B5CF6.toInt()) { launch("trubbi://memo") })
        card.addView(divider())
        card.addView(actionRow(R.drawable.ic_stat_logo, getString(R.string.floating_open_app), 0xFF5A5A61.toInt()) { launch("") })
        card.addView(
            actionRow(R.drawable.ic_widget_close, getString(R.string.floating_turn_off), 0xFFE0564E.toInt()) {
                scope.launch { runCatching { settingsRepo.setFloatingLauncher(false) } }
                stopSelf()
            },
        )
        panel = card

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(bubble, LinearLayout.LayoutParams(bubbleSize, bubbleSize))
            addView(
                card,
                LinearLayout.LayoutParams(dp(cardWidthDp), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(10)
                },
            )
        }
        root = container

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = -peekPx()
            y = posY
        }

        // FLAG_WATCH_OUTSIDE_TOUCH 덕에 바깥 터치가 여기로 들어온다.
        container.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE && expanded) {
                collapse()
                true
            } else {
                false
            }
        }

        attachBubbleTouch(bubble)
        wm.addView(container, params)
        applyDockSide()
        updateJourneyRow()
    }

    private fun screenW(): Int = resources.displayMetrics.widthPixels

    /** 도킹된 변(좌/우)에 맞춰 컨테이너 정렬 + 카드 펼침 방향을 정한다. */
    private fun applyDockSide() {
        root?.gravity = if (dockedRight) Gravity.END else Gravity.START
        params.gravity = Gravity.TOP or (if (dockedRight) Gravity.END else Gravity.START)
        runCatching { wm?.updateViewLayout(root, params) }
    }

    private fun menuTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(0xFF9AA0B0.toInt())
        textSize = 12f
        setPadding(dp(16), dp(4), dp(16), dp(8))
    }

    private fun divider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            topMargin = dp(6)
            bottomMargin = dp(6)
            leftMargin = dp(14)
            rightMargin = dp(14)
        }
        setBackgroundColor(0x22FFFFFF)
    }

    /** 카드 한 줄 — 색 원 아이콘 + 라벨. 운전 중에도 누르기 쉽게 터치 영역을 크게 잡는다. */
    private fun actionRow(iconRes: Int, label: String, circleColor: Int, onClick: () -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(16), dp(9))
            isClickable = true
            setOnClickListener { onClick() }
        }
        val icon = ImageView(this).apply {
            setImageResource(iconRes)
            setColorFilter(Color.WHITE)
        }
        val circle = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(circleColor)
            }
            addView(icon, FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER))
        }
        row.addView(circle, LinearLayout.LayoutParams(dp(38), dp(38)))
        row.addView(
            TextView(this).apply {
                tag = "label"
                text = label
                setTextColor(Color.WHITE)
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { marginStart = dp(14) }
            },
        )
        return row
    }

    /** 버블의 화면상 left(px). gravity 가 END 인지 START 인지에 따라 계산이 달라진다. */
    private fun currentLeftPx(): Int =
        if ((params.gravity and Gravity.END) == Gravity.END) {
            screenW() - params.x - bubbleSizePx
        } else {
            params.x
        }

    /** 탭하면 메뉴를 여닫고, 끌면 옮긴다. 놓는 순간 가까운 가장자리로 붙는다(접힌 상태에서만). */
    @Suppress("ClickableViewAccessibility")
    private fun attachBubbleTouch(bubble: View) {
        var downX = 0f
        var downY = 0f
        var startLeft = 0
        var startTop = 0
        var moved = false
        val slop = dp(8)
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    moved = false
                    if (!expanded) {
                        xAnimator?.cancel()
                        // 드래그 중에는 좌상단 절대좌표로 바꿔 계산한다. 같은 자리라 화면은 움직이지 않는다.
                        startLeft = currentLeftPx()
                        startTop = params.y
                        params.gravity = Gravity.TOP or Gravity.START
                        params.x = startLeft
                        runCatching { wm?.updateViewLayout(root, params) }
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!expanded) {
                        val dx = (e.rawX - downX).toInt()
                        val dy = (e.rawY - downY).toInt()
                        if (abs(dx) + abs(dy) > slop) moved = true
                        if (moved) {
                            params.x = startLeft + dx
                            params.y = (startTop + dy).coerceAtLeast(0)
                            runCatching { wm?.updateViewLayout(root, params) }
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) snapToEdge() else toggle()
                    true
                }
                else -> false
            }
        }
    }

    /** 드래그를 놓으면 가까운 가장자리로 붙이고 손잡이만 남긴다. */
    private fun snapToEdge() {
        xAnimator?.cancel()
        posY = params.y
        val centerX = params.x + bubbleSizePx / 2
        dockedRight = centerX > screenW() / 2
        params.gravity = Gravity.TOP or (if (dockedRight) Gravity.END else Gravity.START)
        root?.gravity = if (dockedRight) Gravity.END else Gravity.START
        params.x = -peekPx()
        params.y = posY
        runCatching { wm?.updateViewLayout(root, params) }
        bounceBubble()
    }

    private fun toggle() {
        if (expanded) collapse() else expand()
    }

    /** 창 자체의 x 를 매 프레임 옮긴다. 뷰만 움직이면 창 밖으로 나가 잘리기 때문이다. */
    private fun animateX(to: Int, duration: Long, interpolator: TimeInterpolator, onEnd: () -> Unit = {}) {
        xAnimator?.cancel()
        xAnimator = ValueAnimator.ofInt(params.x, to).apply {
            this.duration = duration
            this.interpolator = interpolator
            addUpdateListener {
                params.x = it.animatedValue as Int
                runCatching { wm?.updateViewLayout(root, params) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd()
            })
            start()
        }
    }

    /** 가장자리에서 버블이 미끄러져 나오고 카드 행이 차례로 떨어진다. */
    private fun expand() {
        val card = panel ?: return
        if (expanded) return
        expanded = true
        xAnimator?.cancel()
        root?.gravity = if (dockedRight) Gravity.END else Gravity.START
        params.gravity = Gravity.TOP or (if (dockedRight) Gravity.END else Gravity.START)
        // 드래그 때 바꿔 둔 절대좌표를 도킹 기준으로 되돌려 놓아야 시작 위치가 튀지 않는다.
        params.x = -peekPx()
        card.alpha = 0f
        card.scaleX = 1f
        card.scaleY = 1f
        card.visibility = View.VISIBLE
        runCatching { wm?.updateViewLayout(root, params) }
        animateX(marginPx(), 360, OvershootInterpolator(1.4f))
        bounceBubble()
        card.animate().alpha(1f).setDuration(200).start()
        var step = 0
        for (i in 0 until card.childCount) {
            val child = card.getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            child.alpha = 0f
            child.translationY = -dp(10).toFloat()
            child.animate().alpha(1f).translationY(0f)
                .setStartDelay(35L * step).setDuration(220)
                .setInterpolator(OvershootInterpolator(1.3f)).start()
            step++
        }
    }

    private fun collapse() {
        val card = panel ?: return
        if (!expanded) return
        expanded = false
        card.animate().alpha(0f).setDuration(150).start()
        // 창 크기를 먼저 줄이면 내용이 잘려 보이므로, 애니메이션 종료 후에 카드를 감춘다.
        animateX(-peekPx(), 200, AccelerateInterpolator()) {
            card.visibility = View.GONE
            card.alpha = 1f
            runCatching { wm?.updateViewLayout(root, params) }
        }
    }

    /** 눌렸다 튀어 오르는 반응. */
    private fun bounceBubble() {
        val bubble = bubbleView ?: return
        bubble.animate().scaleX(0.86f).scaleY(0.86f).setDuration(90)
            .withEndAction {
                bubble.animate().scaleX(1f).scaleY(1f)
                    .setInterpolator(OvershootInterpolator(3.5f)).setDuration(300).start()
            }.start()
    }

    /** 여정 요약과 '다음 단계' 행을 현재 설정·진행 상태에 맞춘다. */
    private fun updateJourneyRow() {
        val active = activeJourney
        val show = journeyControlOn && active != null
        journeyHeader?.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            journeyRouteView?.text = active.routeLabel(this)
                .ifBlank { getString(R.string.floating_journey_running) }
            journeyStageView?.text = getString(
                R.string.floating_current_stage,
                journeyActionLabel(active) ?: getString(R.string.floating_in_progress),
            )
        }
        val label = if (journeyControlOn) journeyActionLabel(active) else null
        journeyRow?.visibility = if (label != null) View.VISIBLE else View.GONE
        if (label != null) journeyLabelView?.text = label
        runCatching { wm?.updateViewLayout(root, params) }
    }

    /** 지금 이 버튼을 누르면 일어날 일. 여정이 없으면 null 이라 행 자체가 숨는다. */
    private fun journeyActionLabel(journey: JourneyWithStops?): String? {
        journey ?: return null
        val current = journey.currentStop
        return when {
            journey.orderedStops.isEmpty() -> getString(R.string.floating_view_journey)
            current == null -> getString(R.string.action_complete_journey)
            else -> when (current.phase) {
                StopPhase.MOVING -> getString(R.string.floating_arrive_at, current.displayName(this))
                StopPhase.WAITING -> getString(JourneyEventType.WORK_STARTED.titleRes, current.kindEnum.label(this))
                StopPhase.WORKING -> getString(JourneyEventType.STOP_COMPLETED.titleRes, current.kindEnum.label(this))
                StopPhase.DONE -> getString(R.string.floating_next_step)
            }
        }
    }

    /**
     * '다음 단계' 탭.
     *
     * 단계 진행은 트래킹 서비스에 넘긴다. 알림·위젯에서 누른 것과 같은 경로를 타야
     * 음성 안내와 앱 내 피드백이 한 번씩만 나온다.
     */
    private fun onJourneyAction() {
        val journey = activeJourney ?: return
        when {
            journey.orderedStops.isEmpty() -> launch("trubbi://journey")
            journey.currentStop == null -> {
                val id = journey.journey.journeyId
                scope.launch { runCatching { journeyRepo.completeJourney(id) } }
                collapse()
            }
            else -> {
                runCatching {
                    startService(
                        Intent(this, JourneyTrackingService::class.java)
                            .setAction(JourneyTrackingService.ACTION_ADVANCE),
                    )
                }
                collapse()
            }
        }
    }

    private fun launch(deepLink: String) {
        val intent = if (deepLink.isBlank()) {
            packageManager.getLaunchIntentForPackage(packageName)
        } else {
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse(deepLink)
            }
        }
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { startActivity(intent) }
        collapse()
    }

    // ── 포그라운드 알림 ───────────────────────────────────────────────────────

    private fun startForegroundNotice() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, getString(R.string.floating_channel_name), NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
            }
            mgr.createNotificationChannel(ch)
        }
        val open = android.app.PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } ?: Intent(this, MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_logo)
            .setLargeIcon(NotificationIcons.glyph(this, R.drawable.ic_stat_truck))
            .setColor(com.naury.trubbi.core.ui.BrandPalette.PRIMARY)
            .setContentTitle(getString(R.string.floating_notif_title))
            .setContentText(getString(R.string.floating_notif_text))
            .setOngoing(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notif, type)
    }

    companion object {
        private const val CHANNEL = "trubbi_floating_launcher"
        private const val NOTIF_ID = 4711

        fun start(ctx: Context) {
            if (!Settings.canDrawOverlays(ctx)) return
            androidx.core.content.ContextCompat.startForegroundService(ctx, Intent(ctx, FloatingLauncherService::class.java))
        }

        fun stop(ctx: Context) {
            runCatching { ctx.stopService(Intent(ctx, FloatingLauncherService::class.java)) }
        }
    }
}
