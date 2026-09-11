package com.naury.trubbi.core.ui

import android.content.Context
import androidx.annotation.ArrayRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 컴포저블 밖(뷰모델·리포지토리·서비스)에서 문자열 리소스를 읽기 위한 얇은 래퍼.
 *
 * 데이터·도메인 레이어가 안드로이드 [Context] 를 직접 참조하지 않도록 하는 경계다.
 * 테스트에서는 이 타입만 대체하면 문구를 검증할 수 있다.
 *
 * @property context 애플리케이션 컨텍스트 (Hilt 주입)
 */
@Singleton
class StringProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** 단순 문자열. */
    operator fun get(@StringRes id: Int): String = context.getString(id)

    /** 포맷 인자가 있는 문자열. */
    operator fun get(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    /** 문자열 배열 리소스. */
    fun array(@ArrayRes id: Int): List<String> = context.resources.getStringArray(id).toList()

    /** 수량에 따라 형태가 달라지는 문자열(영어 복수형 대응). */
    fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
        context.resources.getQuantityString(id, count, *args)
}
