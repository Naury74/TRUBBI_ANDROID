package com.naury.trubbi.core.voice

import android.content.Context
import com.google.firebase.functions.FirebaseFunctions
import com.naury.trubbi.core.log.logFailure
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 자연어 의도 해석 (음성 비서의 두뇌).
 *
 * 온라인이면 Claude Haiku 4.5 기반 `parseIntent` Cloud Function 으로 문맥까지 이해해
 * "아니 그거 말고 부산 신항" 같은 정정도 잡아낸다. 오프라인/실패 시엔 기기 내 휴리스틱
 * ([local])으로 폴백 → 인터넷이 없어도 기본 동작은 보장. 절대 throw 하지 않는다.
 *
 * Anthropic API 키는 앱에 두지 않고 Cloud Function 시크릿에만 존재한다.
 *
 * 여기 있는 한국어 낱말들은 번역 대상이 아니라 **한국어 발화를 알아듣기 위한 규칙**이라
 * 리소스로 빼지 않고 코드에 둔다.
 *
 * @property context 네트워크 상태 확인용 애플리케이션 컨텍스트 (Hilt 주입)
 */
@Singleton
class VoiceNlu @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val functions by lazy { FirebaseFunctions.getInstance() }

    /**
     * 발화 한 마디를 구조화된 의도로 바꾼다.
     *
     * @param transcript 받아쓴 문장
     * @param ctx 지금 어느 단계인지, 화면에 어떤 후보가 떠 있는지 같은 맥락.
     *   같은 “두 번째” 라도 후보 목록이 떠 있을 때와 아닐 때 뜻이 다르다
     * @return 해석 결과. 알아듣지 못하면 [VoiceAction.NONE]
     */
    suspend fun interpret(transcript: String, ctx: VoiceContext): VoiceIntent {
        val t = transcript.trim()
        if (t.isEmpty()) return VoiceIntent(VoiceAction.NONE, raw = t)
        // 속도 우선: 로컬이 알아들으면 즉시 반환(클라우드 왕복 없음). 애매할 때(NONE)만 클라우드로.
        val localIntent = local(t, ctx)
        if (localIntent.action != VoiceAction.NONE) return localIntent
        if (isOnline()) cloud(t, ctx)?.let { return it }
        return localIntent
    }

    // ── 클라우드 해석 (Claude Haiku)

    private suspend fun cloud(transcript: String, ctx: VoiceContext): VoiceIntent? = runCatching {
        val res = withTimeoutOrNull(7000) {
            functions.getHttpsCallable("parseIntent").call(
                mapOf(
                    "transcript" to transcript,
                    "context" to mapOf(
                        "step" to ctx.step,
                        "candidates" to ctx.candidates,
                        "decided" to ctx.decided,
                        "lastPrompt" to ctx.lastPrompt,
                    ),
                ),
            ).await()
        } ?: return null

        @Suppress("UNCHECKED_CAST")
        val data = res.data as? Map<String, Any?> ?: return null

        @Suppress("UNCHECKED_CAST")
        val m = data["intent"] as? Map<String, Any?> ?: return null
        mapIntent(m, transcript)
    }.logFailure(TAG, "클라우드 의도 해석 실패 — 기기 내 규칙으로 대체한다").getOrNull()

    private fun mapIntent(m: Map<String, Any?>, raw: String): VoiceIntent {
        val action = when ((m["action"] as? String)?.lowercase()) {
            "place" -> VoiceAction.PLACE
            "select" -> VoiceAction.SELECT
            "correct" -> VoiceAction.CORRECT
            "current" -> VoiceAction.CURRENT
            "add_stop" -> VoiceAction.ADD_STOP
            "skip" -> VoiceAction.SKIP
            "start" -> VoiceAction.START
            "cancel" -> VoiceAction.CANCEL
            "back" -> VoiceAction.BACK
            "repeat" -> VoiceAction.REPEAT
            "move" -> VoiceAction.MOVE
            else -> VoiceAction.NONE
        }
        val place = (m["place"] as? String)?.trim()?.takeIf { it.isNotBlank() }
        val ordinal = (m["ordinal"] as? Number)?.toInt()?.takeIf { it in 1..30 }
        val kind = when ((m["kind"] as? String)?.lowercase()) {
            "pickup" -> VoiceStopKind.PICKUP
            "dropoff" -> VoiceStopKind.DROPOFF
            else -> null
        }
        val target = when ((m["target"] as? String)?.lowercase()) {
            "place" -> CorrectTarget.PLACE
            "ordinal" -> CorrectTarget.ORDINAL
            "step" -> CorrectTarget.STEP
            else -> null
        }
        val reply = (m["reply"] as? String)?.trim()?.takeIf { it.isNotBlank() }
        val conf = (m["confidence"] as? Number)?.toFloat()?.coerceIn(0f, 1f) ?: 0.5f
        return VoiceIntent(action, place, ordinal, kind, target, reply, conf, raw, fromCloud = true)
    }

    // ───────────────────────── 기기 내 폴백(오프라인) ─────────────────────────

    /** 인터넷이 없을 때를 위한 가벼운 규칙 기반 해석. 클라우드만큼 똑똑하진 않지만 끊기지 않게. */
    private fun local(raw: String, ctx: VoiceContext): VoiceIntent {
        val t = raw.replace(" ", "")

        // 공통 명령 우선.
        if (anyOf(t, "취소", "그만", "닫기", "종료", "나가기", "안할래", "관둘래", "멈춰")) {
            return VoiceIntent(VoiceAction.CANCEL, raw = raw, confidence = 0.6f)
        }
        if (anyOf(t, "이전", "뒤로", "back", "전단계")) {
            return VoiceIntent(VoiceAction.BACK, raw = raw, confidence = 0.5f)
        }
        if (anyOf(t, "다시말", "다시해", "못들었", "뭐라고", "한번더")) {
            return VoiceIntent(VoiceAction.REPEAT, raw = raw, confidence = 0.5f)
        }

        // 정정 표현 — "아니 그거 말고 ~", "틀렸어", "두번째 말고 세번째".
        val correcting = anyOf(t, "아니", "말고", "틀렸", "그게아니", "잘못", "바꿔", "다시할", "아닌데")
        if (correcting) {
            val ord = parseOrdinal(t)
            val place = stripStopWords(raw)
            return when {
                ord != null -> VoiceIntent(VoiceAction.CORRECT, ordinal = ord, correctTarget = CorrectTarget.ORDINAL, raw = raw, confidence = 0.6f)
                place.length >= 2 -> VoiceIntent(VoiceAction.CORRECT, place = place, correctTarget = CorrectTarget.PLACE, raw = raw, confidence = 0.55f)
                else -> VoiceIntent(VoiceAction.CORRECT, correctTarget = CorrectTarget.STEP, raw = raw, confidence = 0.5f)
            }
        }

        // 순서 이동 — "○○ 먼저", "○○ 부터", "○○ 가는 걸로", "~ 가기 전에 ○○". (출발/시작보다 먼저 검사)
        if (anyOf(t, "먼저", "부터", "가는걸로", "제일먼저", "순서바꿔", "가기전에", "전에가")) {
            val place = stripStopWords(raw)
            if (place.length >= 2) return VoiceIntent(VoiceAction.MOVE, place = place, raw = raw, confidence = 0.55f)
        }
        // "가자"·"출발"은 "부산 신항 가자"처럼 목적지와 함께 오는 일이 많다. 그 말만 했을 때만 시작으로 본다.
        if (anyOf(t, "여정시작", "바로시작", "지금시작", "바로출발", "지금출발", "시작해", "시작할", "출발하자", "고고", "스타트", "떠나자", "추가완료", "다됐", "다했", "이대로") ||
            t in START_ALONE
        ) {
            return VoiceIntent(VoiceAction.START, raw = raw, confidence = 0.6f)
        }
        if (anyOf(t, "경유지추가", "경유추가", "경유", "들렀다", "들러서", "거쳐", "들를곳")) {
            return VoiceIntent(VoiceAction.ADD_STOP, kind = stopKind(t), raw = raw, confidence = 0.55f)
        }
        // "현장"은 "현장 사무소" 같은 장소 이름에도 들어가 현재 위치로 오인됐다. 낱말 전체가 같을 때만 본다.
        if (PlaceUtterance.HERE_WORDS.any(t::contains) || t in PlaceUtterance.HERE_ALONE) {
            return VoiceIntent(VoiceAction.CURRENT, raw = raw, confidence = 0.6f)
        }
        if (anyOf(t, "나중에", "건너뛰", "생략", "스킵", "패스", "넘어가", "다음에", "일단없", "없어", "없다", "없습니다", "없네", "다됐", "다했")) {
            return VoiceIntent(VoiceAction.SKIP, raw = raw, confidence = 0.5f)
        }

        // pick 단계: 순번/긍정 → 선택.
        if (ctx.step == "pick") {
            parseOrdinal(t)?.let { return VoiceIntent(VoiceAction.SELECT, ordinal = it, raw = raw, confidence = 0.6f) }
            if (isAffirmative(t)) return VoiceIntent(VoiceAction.SELECT, ordinal = 1, raw = raw, confidence = 0.55f)
            if (anyOf(
                    t,
                    "마지막",
                    "맨끝",
                    "맨뒤",
                )
            ) {
                return VoiceIntent(VoiceAction.SELECT, ordinal = ctx.candidates.size.coerceAtLeast(1), raw = raw, confidence = 0.55f)
            }
        }

        // origin_yesno 단계: 긍정 → 현재 위치.
        if (ctx.step == "origin_yesno" && isAffirmative(t)) {
            return VoiceIntent(VoiceAction.CURRENT, raw = raw, confidence = 0.6f)
        }

        // 그 외엔 장소로 취급.
        val place = stripStopWords(raw)
        return if (place.length >= 2) {
            VoiceIntent(VoiceAction.PLACE, place = place, kind = stopKind(t), raw = raw, confidence = 0.4f)
        } else {
            VoiceIntent(VoiceAction.NONE, raw = raw, confidence = 0.2f)
        }
    }

    private fun stopKind(t: String): VoiceStopKind? = when {
        anyOf(t, "상차", "픽업", "싣", "실어", "실을", "적재") -> VoiceStopKind.PICKUP
        anyOf(t, "하차", "내려", "내릴", "하역", "배송", "납품") -> VoiceStopKind.DROPOFF
        else -> null
    }

    private fun anyOf(t: String, vararg words: String) = words.any { t.contains(it) }

    private fun isAffirmative(t: String): Boolean {
        val exact = setOf("네", "예", "응", "어", "맞아", "맞아요", "맞습니다", "맞다", "그래", "그래요", "좋아", "좋아요", "거기", "그거", "이거", "오케이", "오키", "ok", "확인")
        return t in exact || t.startsWith("맞아") || t.startsWith("그래") || t.startsWith("거기") || t.startsWith("이거")
    }

    private fun parseOrdinal(text: String): Int? {
        val t = text.trim().replace(" ", "")
        if (t.isEmpty()) return null
        Regex("([0-9]{1,2})").find(t)?.groupValues?.get(1)?.toIntOrNull()?.let { if (it in 1..30) return it }
        val strong = listOf(
            1 to listOf("첫번째", "첫번", "첫째", "처음", "맨처음", "맨앞", "일번", "한번째"),
            // "이번"은 "이번엔 ○○" 처럼 순번이 아닌 뜻으로 더 자주 쓰여 단독으로 말했을 때만 2번으로 본다.
            2 to listOf("두번째", "둘째", "이번째", "두번"),
            3 to listOf("세번째", "셋째", "세번", "삼번째", "삼번"),
            4 to listOf("네번째", "넷째", "네번", "사번째", "사번"),
            5 to listOf("다섯번째", "다섯째", "다섯번", "오번째", "오번"),
            6 to listOf("여섯번째", "여섯째", "여섯번", "육번"),
            7 to listOf("일곱번째", "일곱번", "칠번"),
            8 to listOf("여덟번째", "여덟번", "팔번"),
            9 to listOf("아홉번째", "아홉번", "구번"),
        )
        for ((n, ws) in strong) if (ws.any { t.contains(it) }) return n
        return mapOf(
            "하나" to 1, "첫" to 1, "둘" to 2, "이번" to 2, "셋" to 3, "넷" to 4, "다섯" to 5, "여섯" to 6, "일곱" to 7, "여덟" to 8, "아홉" to 9,
        )[t]
    }

    private fun stripStopWords(text: String): String = PlaceUtterance.query(text)

    private fun isOnline(): Boolean = context.hasValidatedNetwork()

    private companion object {
        const val TAG = "VoiceNlu"

        /** 이 말만 했을 때 여정 시작으로 보는 짧은 대답. */
        val START_ALONE = setOf("가자", "가자고", "출발", "출발해", "출발할게", "출발할게요", "시작", "완료", "됐어", "됐어요", "고")
    }
}
