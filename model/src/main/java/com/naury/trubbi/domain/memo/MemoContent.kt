package com.naury.trubbi.domain.memo

import org.json.JSONArray
import org.json.JSONObject

/**
 * 블록 단위 메모 내용.
 *
 * 메모 하나는 블록들의 순서 있는 목록이고, JSON 으로 직렬화해 기록의 `memo` 컬럼에 그대로 넣는다.
 * 표나 이미지를 섞으려면 통짜 문자열로는 감당이 안 되는데, 그렇다고 테이블을 더 만들자니
 * 메모 한 건을 읽을 때마다 조인이 붙는다. JSON 한 칸이 이 규모에는 맞았다.
 *
 * 편집기와 상세 화면이 **같은 블록 목록**을 읽는다. 두 벌로 나누면 편집에서 보이던 모양과
 * 저장 후 모양이 어긋나기 시작한다.
 */
sealed interface MemoBlock {
    /** 예전 형식 — 문단 전체에 같은 스타일. 새 메모는 [Html] 을 쓰고, 이건 읽기만 지원한다. */
    data class Text(
        val text: String = "",
        val color: Long? = null,
        val sizeSp: Int = 17,
        val bold: Boolean = false,
    ) : MemoBlock

    /** HTML 로 저장하는 서식 있는 문단. 메일 편집기처럼 선택한 범위에만 스타일이 걸린다. */
    data class Html(val html: String) : MemoBlock

    /** 본문에 끼워 넣은 이미지. 앱 저장소의 uri 를 가리킨다. */
    data class Image(val uri: String) : MemoBlock

    /** 문자열 칸으로 된 표. [rows] 는 행 우선이고 모든 행의 길이가 같다. */
    data class Table(val rows: List<List<String>>) : MemoBlock
}

object MemoContent {

    // 이미지 블록은 검색 색인·제목에서 자리만 차지하면 된다.
    private const val IMAGE_PLACEHOLDER = "[🖼]"

    /** 블록 형식임을 알리는 접두사. 이게 없으면 옛 평문 메모로 본다. */
    private const val PREFIX = "@@RICH1@@"

    fun isRich(memo: String?): Boolean = memo?.startsWith(PREFIX) == true

    fun encode(blocks: List<MemoBlock>): String {
        val arr = JSONArray()
        blocks.forEach { block ->
            val o = JSONObject()
            when (block) {
                is MemoBlock.Text -> {
                    o.put("t", "text")
                    o.put("text", block.text)
                    block.color?.let { o.put("color", it) }
                    o.put("size", block.sizeSp)
                    o.put("bold", block.bold)
                }
                is MemoBlock.Html -> {
                    o.put("t", "html")
                    o.put("html", block.html)
                }
                is MemoBlock.Image -> {
                    o.put("t", "image")
                    o.put("uri", block.uri)
                }
                is MemoBlock.Table -> {
                    o.put("t", "table")
                    val rowsArr = JSONArray()
                    block.rows.forEach { row ->
                        val rowArr = JSONArray()
                        row.forEach { rowArr.put(it) }
                        rowsArr.put(rowArr)
                    }
                    o.put("rows", rowsArr)
                }
            }
            arr.put(o)
        }
        return PREFIX + arr.toString()
    }

    /**
     * 저장된 메모 문자열을 블록 목록으로 푼다.
     *
     * @param raw DB 에 있는 메모 문자열. 접두사가 없으면 문단 하나짜리 블록으로 감싼다
     */
    fun decode(memo: String?): List<MemoBlock> {
        if (memo.isNullOrBlank()) return emptyList()
        if (!isRich(memo)) return listOf(MemoBlock.Text(text = memo))
        return runCatching {
            val arr = JSONArray(memo.removePrefix(PREFIX))
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    when (o.optString("t")) {
                        "text" -> add(
                            MemoBlock.Text(
                                text = o.optString("text", ""),
                                color = if (o.has("color")) o.optLong("color") else null,
                                sizeSp = o.optInt("size", 17),
                                bold = o.optBoolean("bold", false),
                            ),
                        )
                        "html" -> add(MemoBlock.Html(o.optString("html", "")))
                        "image" -> o.optString("uri").takeIf { it.isNotBlank() }?.let { add(MemoBlock.Image(it)) }
                        "table" -> {
                            val rowsArr = o.optJSONArray("rows")
                            val rows = buildList {
                                if (rowsArr != null) {
                                    for (r in 0 until rowsArr.length()) {
                                        val rowArr = rowsArr.optJSONArray(r) ?: JSONArray()
                                        add((0 until rowArr.length()).map { rowArr.optString(it) })
                                    }
                                }
                            }
                            if (rows.isNotEmpty()) add(MemoBlock.Table(rows))
                        }
                    }
                }
            }
        }.getOrElse { listOf(MemoBlock.Text(text = memo.removePrefix(PREFIX))) }
    }

    /** 서식을 걷어 낸 평문. 기록 제목을 뽑고 검색 색인에 넣는 데 쓴다. */
    fun plainText(blocks: List<MemoBlock>): String = blocks.joinToString("\n") { block ->
        when (block) {
            is MemoBlock.Text -> block.text
            is MemoBlock.Html -> htmlToPlain(block.html)
            is MemoBlock.Image -> IMAGE_PLACEHOLDER
            is MemoBlock.Table -> block.rows.joinToString("\n") { it.joinToString(" | ") }
        }
    }.trim()

    /**
     * 첫 번째 비어 있지 않은 줄로 제목을 만든다.
     *
     * @param fallback 글자가 하나도 없을 때 쓸 이름 (호출부가 언어에 맞춰 넘긴다)
     */
    fun deriveTitle(blocks: List<MemoBlock>, fallback: String): String =
        blocks.asSequence()
            .flatMap { block ->
                when (block) {
                    is MemoBlock.Text -> block.text.lineSequence()
                    is MemoBlock.Html -> htmlToPlain(block.html).lineSequence()
                    else -> emptySequence()
                }
            }
            .firstOrNull { it.isNotBlank() }
            ?.take(40)
            ?: fallback

    /** 태그를 걷고 자주 쓰는 엔티티를 되돌린다. 검색·미리보기·제목용. */
    fun htmlToPlain(html: String): String = html
        .replace(Regex("(?i)<br ?/?>"), "\n")
        .replace(Regex("(?i)</(p|div|li|h[1-6]|tr)>"), "\n")
        .replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .trim()

    /** 옛 문단 스타일을 같은 모양의 HTML 로 옮긴다. 한 방향으로만 간다. */
    fun textToHtml(block: MemoBlock.Text): String {
        val esc = block.text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")
        val styles = buildList {
            block.color?.let { add("color:#%06X".format(it.toInt() and 0xFFFFFF)) }
            if (block.sizeSp != 17) add("font-size:${block.sizeSp}px")
            if (block.bold) add("font-weight:bold")
        }
        return if (styles.isEmpty()) {
            "<p>$esc</p>"
        } else {
            "<p><span style=\"${styles.joinToString(";")}\">$esc</span></p>"
        }
    }
}
