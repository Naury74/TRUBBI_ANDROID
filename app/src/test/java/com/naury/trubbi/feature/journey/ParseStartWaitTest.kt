package com.naury.trubbi.feature.journey

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ParseStartWaitTest {

    @Test
    fun `now 는 no 가 들어 있어도 시작이다`() {
        assertThat(parseStartWait("now")).isTrue()
        assertThat(parseStartWait("start now")).isTrue()
    }

    @Test
    fun `짧은 대답은 낱말 전체가 같을 때만 인정한다`() {
        assertThat(parseStartWait("네")).isTrue()
        assertThat(parseStartWait("no")).isFalse()
        // "고"·"어"가 들어 있을 뿐인 말은 대답이 아니다.
        assertThat(parseStartWait("고속도로")).isNull()
        assertThat(parseStartWait("어디야")).isNull()
    }

    @Test
    fun `어미가 붙은 긴 대답을 알아듣는다`() {
        assertThat(parseStartWait("바로 시작할게요")).isTrue()
        assertThat(parseStartWait("조금 있다가 할게요")).isFalse()
        assertThat(parseStartWait("대기할게")).isFalse()
    }

    @Test
    fun `빈 말은 해석하지 않는다`() {
        assertThat(parseStartWait("  ")).isNull()
    }
}
