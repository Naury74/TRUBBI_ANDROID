package com.naury.trubbi.feature.journey.planner

import com.google.common.truth.Truth.assertThat
import com.naury.trubbi.core.location.PlaceCandidate
import kotlinx.coroutines.test.runTest
import org.junit.Test

class VoicePlaceRankerTest {

    private fun place(name: String) = PlaceCandidate(name, null, 0.0, 0.0)

    @Test
    fun `1순위에 결과가 없으면 결과가 있는 다음 후보를 고른다`() = runTest {
        val db = mapOf("평택항" to listOf(place("평택항 국제여객터미널")))

        val pick = pickVoiceQuery(listOf("평택 앙", "평택항")) { db[it].orEmpty() }

        assertThat(pick!!.query).isEqualTo("평택항")
        assertThat(pick.others).containsExactly("평택 앙")
    }

    @Test
    fun `이름이 맞는 후보가 순위가 낮아도 이긴다`() = runTest {
        val db = mapOf(
            "시낭" to listOf(place("시낭 카페")),
            "부산 신항" to listOf(place("부산신항")),
        )

        // "시낭 카페"는 이름이 겹치긴 하지만 부분 일치다. "부산신항"은 띄어쓰기만 다른 완전 일치라 더 높다.
        val pick = pickVoiceQuery(listOf("시낭", "부산 신항")) { db[it].orEmpty() }

        assertThat(pick!!.query).isEqualTo("부산 신항")
    }

    @Test
    fun `엇비슷하면 인식기 1순위를 따른다`() = runTest {
        val pick = pickVoiceQuery(listOf("인천항", "인천 항")) { listOf(place("$it 물류센터")) }

        assertThat(pick!!.query).isEqualTo("인천항")
    }

    @Test
    fun `모두 결과가 없으면 1순위를 그대로 둔다`() = runTest {
        val pick = pickVoiceQuery(listOf("가나다", "라마바")) { emptyList() }

        assertThat(pick!!.query).isEqualTo("가나다")
        assertThat(pick.results).isEmpty()
    }

    @Test
    fun `후보가 없으면 고르지 않는다`() = runTest {
        assertThat(pickVoiceQuery(emptyList()) { emptyList() }).isNull()
    }
}
