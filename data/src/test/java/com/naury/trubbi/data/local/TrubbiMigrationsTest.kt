package com.naury.trubbi.data.local

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * DB 버전을 올릴 때 빠뜨리기 쉬운 것들을 막는다.
 *
 * 버전만 올리고 마이그레이션을 안 쓰면 개발 기기에서는 앱을 지웠다 깔기 때문에 아무 일도
 * 일어나지 않는다. 실제로는 이미 앱을 쓰던 사람이 업데이트하는 순간 처음 드러난다.
 * 그 시차를 없애려고, 버전·스키마·마이그레이션이 어긋나면 여기서 먼저 실패시킨다.
 */
class TrubbiMigrationsTest {

    // @Database 의 version 은 이 값을 그대로 쓴다(런타임에 애노테이션이 남지 않아 상수로 읽는다).
    private val version = TrubbiMigrations.CURRENT_VERSION

    /** 유닛 테스트의 작업 디렉터리는 실행 방식에 따라 모듈이거나 저장소 루트다. */
    private fun schemaDir(): File =
        listOf(File("schemas"), File("data/schemas"))
            .firstOrNull { it.isDirectory }
            ?.resolve(TrubbiDatabase::class.java.name)
            ?: error("schemas 디렉터리를 찾지 못했다 (room.schemaLocation 설정 확인)")

    @Test
    fun `현재 버전의 스키마가 내보내져 있다`() {
        val file = schemaDir().resolve("$version.json")
        assertThat(file.isFile).isTrue()
        assertThat(file.length()).isGreaterThan(0L)
    }

    @Test
    fun `스키마를 내보내기 시작한 버전 이후의 스키마가 모두 남아 있다`() {
        val missing = (TrubbiMigrations.FIRST_EXPORTED_VERSION..version)
            .filterNot { schemaDir().resolve("$it.json").isFile }

        assertThat(missing).isEmpty()
    }

    @Test
    fun `마이그레이션이 끊김 없이 현재 버전까지 이어진다`() {
        var at = TrubbiMigrations.FIRST_EXPORTED_VERSION
        val steps = TrubbiMigrations.ALL.sortedBy { it.startVersion }
        for (step in steps) {
            assertThat(step.startVersion).isEqualTo(at)
            at = step.endVersion
        }
        assertThat(at).isEqualTo(version)
    }

    @Test
    fun `파괴적 폴백은 스키마 기록이 없는 구간에만 열려 있다`() {
        val destructive = TrubbiMigrations.PRE_EXPORT_VERSIONS.toList()

        assertThat(destructive).isEqualTo((1 until TrubbiMigrations.FIRST_EXPORTED_VERSION).toList())
        assertThat(destructive).doesNotContain(TrubbiMigrations.FIRST_EXPORTED_VERSION)
        assertThat(destructive).doesNotContain(version)
    }
}
