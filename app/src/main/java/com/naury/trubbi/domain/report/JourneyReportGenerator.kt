package com.naury.trubbi.domain.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.naury.trubbi.R
import com.naury.trubbi.core.file.MediaStorage
import com.naury.trubbi.core.i18n.label
import com.naury.trubbi.core.log.logFailure
import com.naury.trubbi.data.i18n.displayName
import com.naury.trubbi.data.i18n.displayTitle
import com.naury.trubbi.data.i18n.routeLabel
import com.naury.trubbi.data.local.JourneyDao
import com.naury.trubbi.data.local.MediaEntity
import com.naury.trubbi.data.local.RecordWithMedia
import com.naury.trubbi.domain.model.EvidenceCategory
import com.naury.trubbi.domain.model.ExpenseCategory
import com.naury.trubbi.domain.model.IssueType
import com.naury.trubbi.domain.model.RecordType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 여정 증빙 리포트를 A4 PDF 로 만든다.
 *
 * 플랫폼의 [PdfDocument] 만 쓰므로 외부 PDF 라이브러리 의존이 없다. 본문 페이지는
 * 커서 기반으로 흘려 쓰다 넘치면 자동으로 다음 장으로 넘어가고, 뒤이어 워터마크를 새긴
 * 증빙 사진 페이지가 2×3 격자로 붙는다. 원본 사진은 건드리지 않는다.
 *
 * @property context 캐시 디렉터리·FileProvider·문자열 리소스용
 * @property dao 리포트에 담을 여정 데이터 조회
 * @property mediaStorage 사진 디코딩
 */
@Singleton
class JourneyReportGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: JourneyDao,
    private val mediaStorage: MediaStorage,
) {
    private val dateTimeFmt get() = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())
    private val timeFmt get() = SimpleDateFormat("HH:mm", Locale.getDefault())

    /** 공유 가능한 캐시에 PDF 를 만들고 content uri 를 돌려준다. 실패하면 null. */
    suspend fun generatePdf(journeyId: String): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val journey = dao.journeyWithStopsOnce(journeyId) ?: return@runCatching null
            val records = dao.journeyRecordsOnce(journeyId)
            val expenses = dao.expensesOnce(journeyId)
            val issues = dao.issuesOnce(journeyId)
            val events = dao.eventsOnce(journeyId)

            val pdf = PdfDocument()
            val writer = PageWriter(pdf)

            writer.title(journey.routeLabel(context))
            writer.caption(str(R.string.report_subtitle, dateTimeFmt.format(Date())))
            writer.divider()

            val info = journey.journey
            writer.row(
                str(R.string.report_period),
                buildString {
                    append(dateTimeFmt.format(Date(info.startedAt)))
                    info.completedAt?.let { append("  ~  ${dateTimeFmt.format(Date(it))}") }
                },
            )
            if (info.totalDistanceM > 0) writer.row(str(R.string.report_distance), formatKm(info.totalDistanceM))
            info.cargoName?.let { writer.row(str(R.string.report_cargo), it) }
            writer.row(
                str(R.string.report_records),
                str(R.string.report_records_value, records.size, records.count { it.media.isNotEmpty() }),
            )
            val totalExpense = expenses.sumOf { it.amount }
            if (totalExpense > 0) writer.row(str(R.string.report_total_expense), money(totalExpense))
            info.freightAmount?.let { freight ->
                writer.row(str(R.string.report_freight), money(freight))
                writer.row(str(R.string.report_net_profit), money(freight - totalExpense))
            }
            writer.space(10f)

            writer.section(str(R.string.report_section_stops))
            journey.orderedStops.forEachIndexed { i, stop ->
                val times = buildList {
                    stop.arrivedAt?.let { add(str(R.string.report_stop_arrived, timeFmt.format(Date(it)))) }
                    stop.waitingMs?.takeIf { it > 60_000 }
                        ?.let { add(str(R.string.report_stop_waited, (it / 60_000).toInt())) }
                    stop.completedAt?.let { add(str(R.string.report_stop_completed, timeFmt.format(Date(it)))) }
                }
                writer.bullet("${i + 1}. [${stop.kindEnum.label(context)}] ${stop.displayName(context)}")
                stop.address?.let { writer.sub(it) }
                if (times.isNotEmpty()) writer.sub(times.joinToString(" · "))
            }

            if (expenses.isNotEmpty()) {
                writer.space(8f)
                writer.section(str(R.string.report_section_expenses))
                expenses.forEach { expense ->
                    writer.bullet(
                        str(
                            R.string.report_expense_line,
                            ExpenseCategory.fromName(expense.category).label(context),
                            "%,d".format(expense.amount),
                        ) +
                            (expense.vendorName?.let { " · $it" }.orEmpty()) +
                            " · ${dateTimeFmt.format(Date(expense.usedAt))}",
                    )
                }
            }

            if (issues.isNotEmpty()) {
                writer.space(8f)
                writer.section(str(R.string.report_section_issues, issues.size), color = ISSUE_RED)
                issues.forEach { issue ->
                    writer.bullet(
                        "[${IssueType.fromName(issue.type).label(context)}] " +
                            dateTimeFmt.format(Date(issue.occurredAt)),
                    )
                    writer.sub(issue.description)
                    issue.address?.let { writer.sub(it) }
                }
            }

            if (events.isNotEmpty()) {
                writer.space(8f)
                writer.section(str(R.string.report_section_timeline))
                events.forEach { event ->
                    writer.bullet(
                        "${dateTimeFmt.format(Date(event.at))}  ${event.displayTitle(context)}" +
                            (event.description?.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()),
                    )
                }
            }
            writer.finishPage()

            val photos = records
                .filter { RecordType.fromName(it.record.type) == RecordType.PHOTO || it.hasVideo }
                .flatMap { rec -> rec.media.filter { !it.isVideo }.map { rec to it } }
                .take(MAX_REPORT_PHOTOS)
            photos.chunked(PHOTOS_PER_PAGE).forEach { writer.photoPage(it) }

            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val file = File(dir, str(R.string.report_file_name, stamp))
            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.logFailure(TAG, "여정 리포트 PDF 생성 실패").getOrNull()
    }

    /** 여정 사진마다 워터마크를 새긴 사본 — ACTION_SEND_MULTIPLE 로 바로 보낼 수 있다. */
    suspend fun watermarkedShareUris(journeyId: String): List<Uri> = withContext(Dispatchers.IO) {
        val records = runCatching { dao.journeyRecordsOnce(journeyId) }
            .logFailure(TAG, "공유용 사진 조회 실패")
            .getOrNull()
            ?: return@withContext emptyList()
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        records.flatMap { rec -> rec.media.filter { !it.isVideo }.map { rec to it } }
            .take(MAX_SHARE_PHOTOS)
            .mapNotNull { (rec, media) ->
                runCatching {
                    val source = mediaStorage.decodeForEditing(media.uri)
                    val marked = Watermark.apply(source, watermarkLines(rec))
                    val out = File(dir, "wm_${media.mediaId.take(8)}.jpg")
                    FileOutputStream(out).use { marked.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
                }.logFailure(TAG, "사진 워터마크 처리 실패 — 이 장만 공유에서 빠진다").getOrNull()
            }
    }

    private fun watermarkLines(rec: RecordWithMedia): List<String> = buildList {
        add(dateTimeFmt.format(Date(rec.record.createdAt)))
        rec.record.locationName?.takeIf { it.isNotBlank() }?.let { add(it) }
        EvidenceCategory.fromName(rec.record.evidenceCategory)?.let { add(it.label(context)) }
    }

    private fun str(resId: Int, vararg args: Any): String = context.getString(resId, *args)

    private fun money(amount: Long): String = str(R.string.amount_krw, "%,d".format(amount))

    private fun formatKm(meters: Long): String =
        if (meters >= 1000) "%.1f km".format(meters / 1000f) else "$meters m"

    /**
     * A4 한 장을 위에서 아래로 채워 나가는 커서형 작성기.
     *
     * 남은 높이가 모자라면 [ensurePage] 가 알아서 다음 장을 연다.
     */
    private inner class PageWriter(private val pdf: PdfDocument) {
        private val pageW = 595
        private val pageH = 842
        private val margin = 46f
        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = 0f

        private val ink = 0xFF191F28.toInt()
        private val gray = 0xFF6B7684.toInt()
        private val rule = 0xFFE5E8EB.toInt()

        private fun paint(size: Float, color: Int = ink, bold: Boolean = false) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = size
                this.color = color
                isFakeBoldText = bold
            }

        /** 남은 높이가 모자라면 새 장을 열고, 어느 쪽이든 그릴 수 있는 캔버스를 돌려준다. */
        private fun ensurePage(needed: Float = 24f): Canvas {
            val current = canvas
            if (current != null && y + needed <= pageH - margin) return current
            return newPage()
        }

        private fun newPage(): Canvas {
            finishPage()
            pageNo++
            val opened = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            page = opened
            canvas = opened.canvas
            y = margin
            return opened.canvas
        }

        fun finishPage() {
            page?.let {
                it.canvas.drawText("TRUBBI · $pageNo", margin, pageH - 20f, paint(8.5f, gray))
                pdf.finishPage(it)
            }
            page = null
            canvas = null
        }

        fun title(text: String) {
            val canvas = ensurePage(34f)
            val p = paint(20f, bold = true)
            canvas.drawText(ellipsize(text, p, pageW - margin * 2), margin, y + 20f, p)
            y += 32f
        }

        fun caption(text: String) {
            ensurePage(16f).drawText(text, margin, y + 10f, paint(9.5f, gray))
            y += 18f
        }

        fun divider() {
            val p = Paint().apply {
                color = rule
                strokeWidth = 1.2f
            }
            ensurePage(14f).drawLine(margin, y + 6f, pageW - margin, y + 6f, p)
            y += 16f
        }

        fun row(label: String, value: String) {
            val canvas = ensurePage(18f)
            canvas.drawText(label, margin, y + 12f, paint(10.5f, gray))
            val p = paint(10.5f)
            canvas.drawText(ellipsize(value, p, pageW - margin * 2 - 96f), margin + 96f, y + 12f, p)
            y += 18f
        }

        fun section(text: String, color: Int = ink) {
            ensurePage(26f).drawText(text, margin, y + 16f, paint(13f, color, bold = true))
            y += 24f
        }

        fun bullet(text: String) {
            val canvas = ensurePage(16f)
            val p = paint(10.5f)
            canvas.drawText("•", margin, y + 11f, p)
            canvas.drawText(ellipsize(text, p, pageW - margin * 2 - 14f), margin + 12f, y + 11f, p)
            y += 16f
        }

        fun sub(text: String) {
            val p = paint(9.5f, gray)
            ensurePage(14f).drawText(ellipsize(text, p, pageW - margin * 2 - 14f), margin + 12f, y + 10f, p)
            y += 14f
        }

        fun space(amount: Float) {
            y += amount
        }

        /** 증빙 사진 한 페이지 — 워터마크를 새긴 사진 최대 6장과 캡션. */
        fun photoPage(items: List<Pair<RecordWithMedia, MediaEntity>>) {
            val canvas = newPage()
            section(str(R.string.report_section_photos))

            val cellW = (pageW - margin * 2 - 14f) / 2f
            val cellH = 222f
            items.forEachIndexed { i, (rec, media) ->
                val x0 = margin + (i % 2) * (cellW + 14f)
                val y0 = y + (i / 2) * (cellH + 30f)
                val source = runCatching { mediaStorage.decodeForEditing(media.uri) }
                    .logFailure(TAG, "리포트에 넣을 사진 디코딩 실패 — 이 칸은 비워 둔다")
                    .getOrNull() ?: return@forEachIndexed
                val marked = Watermark.apply(source, watermarkLines(rec))
                val scale = minOf(cellW / marked.width, (cellH - 26f) / marked.height)
                val drawW = marked.width * scale
                val drawH = marked.height * scale
                val left = x0 + (cellW - drawW) / 2f
                canvas.drawBitmap(
                    marked,
                    null,
                    RectF(left, y0, left + drawW, y0 + drawH),
                    Paint(Paint.FILTER_BITMAP_FLAG),
                )
                val caption = buildString {
                    EvidenceCategory.fromName(rec.record.evidenceCategory)
                        ?.let { append("${it.label(context)} · ") }
                    append(dateTimeFmt.format(Date(rec.record.createdAt)))
                }
                val p = paint(8.5f, gray)
                canvas.drawText(ellipsize(caption, p, cellW), x0, y0 + drawH + 13f, p)
                if (marked !== source) marked.recycle()
            }
        }

        private fun ellipsize(text: String, p: Paint, maxWidth: Float): String {
            if (p.measureText(text) <= maxWidth) return text
            var t = text
            while (t.isNotEmpty() && p.measureText("$t…") > maxWidth) t = t.dropLast(1)
            return "$t…"
        }
    }

    private companion object {
        const val TAG = "JourneyReport"
        const val MAX_REPORT_PHOTOS = 60
        const val PHOTOS_PER_PAGE = 6
        const val MAX_SHARE_PHOTOS = 40
        val ISSUE_RED = 0xFFB3261E.toInt()
    }
}

/**
 * 사진 사본에 촬영 일시·주소·증빙 분류를 새긴다.
 *
 * 사진 왼쪽 아래에 촬영 시각·위치를 굽는다.
 * 원본 비트맵은 그대로 두고 항상 복사본에 그린다.
 */
object Watermark {

    fun apply(src: Bitmap, lines: List<String>): Bitmap {
        if (lines.isEmpty()) return src
        val out = src.copy(Bitmap.Config.ARGB_8888, true) ?: return src
        val canvas = Canvas(out)
        val textSize = (out.width * 0.030f).coerceIn(22f, 58f)
        val pad = textSize * 0.6f
        val lineH = textSize * 1.32f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            isFakeBoldText = true
            setShadowLayer(textSize * 0.08f, 0f, textSize * 0.05f, Color.argb(160, 0, 0, 0))
        }
        val blockW = lines.maxOf { textPaint.measureText(it) } + pad * 2
        val blockH = lineH * lines.size + pad * 1.4f
        val left = out.width * 0.025f
        val top = out.height - blockH - out.height * 0.025f

        val backing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 0, 0, 0) }
        canvas.drawRoundRect(
            RectF(left, top, left + blockW, top + blockH),
            textSize * 0.45f,
            textSize * 0.45f,
            backing,
        )

        lines.forEachIndexed { i, line ->
            canvas.drawText(line, left + pad, top + pad * 0.9f + lineH * (i + 1) - lineH * 0.32f, textPaint)
        }
        return out
    }
}
