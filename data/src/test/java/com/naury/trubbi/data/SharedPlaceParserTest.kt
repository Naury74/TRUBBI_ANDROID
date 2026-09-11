package com.naury.trubbi.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 지도앱 공유 텍스트 파서 테스트.
 *
 * 앱마다 형식이 제각각이고 예고 없이 바뀐다. 그래서 정확한 파싱이 아니라
 * "이름은 첫 줄, 주소는 주소처럼 생긴 줄"이라는 최소 규칙만 지키면 된다.
 */
class SharedPlaceParserTest {

    @Test
    fun `카카오맵 공유는 이름과 주소를 뽑아낸다`() {
        val shared = SharedPlaceParser.parse(
            "성남종합물류센터\n경기 성남시 중원구 사기막골로 105\nhttp://kko.to/abcd",
        )

        assertThat(shared?.name).isEqualTo("성남종합물류센터")
        assertThat(shared?.address).isEqualTo("경기 성남시 중원구 사기막골로 105")
    }

    @Test
    fun `네이버 지도의 대괄호 머리말은 버린다`() {
        val shared = SharedPlaceParser.parse(
            "[네이버 지도]\n부산신항 3부두\n부산광역시 강서구 신항로 100\nhttps://naver.me/abcd",
        )

        assertThat(shared?.name).isEqualTo("부산신항 3부두")
        assertThat(shared?.address).isEqualTo("부산광역시 강서구 신항로 100")
    }

    @Test
    fun `주소처럼 보이는 줄이 없으면 이름만 남긴다`() {
        val shared = SharedPlaceParser.parse("김사장님 창고\nhttps://surl.tmap.co.kr/abcd")

        assertThat(shared?.name).isEqualTo("김사장님 창고")
        assertThat(shared?.address).isNull()
    }

    @Test
    fun `숫자가 없는 짧은 줄은 주소로 보지 않는다`() {
        val shared = SharedPlaceParser.parse("한밭물류\n영업시간 참고\n대전광역시 유성구 테크노로 12")

        assertThat(shared?.address).isEqualTo("대전광역시 유성구 테크노로 12")
    }

    @Test
    fun `빈 텍스트는 무시한다`() {
        assertThat(SharedPlaceParser.parse(null)).isNull()
        assertThat(SharedPlaceParser.parse("   ")).isNull()
        assertThat(SharedPlaceParser.parse("https://kko.to/abcd")).isNull()
    }
}
