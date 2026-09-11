package com.naury.trubbi.core.i18n

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.naury.trubbi.core.ui.StringProvider
import com.naury.trubbi.domain.model.Labeled
import com.naury.trubbi.domain.model.Purpose

/**
 * 열거형·저장된 토큰을 현재 언어의 표시 문구로 바꾸는 확장 모음.
 *
 * 라벨 해석을 여기 한 곳에 모아 두면, 화면(컴포저블)·뷰모델·서비스가 각자
 * 다른 방식으로 문구를 만들어 쓰는 일을 막을 수 있다.
 */

/** 컴포저블 안에서 쓰는 라벨. 언어를 바꾸면 자동으로 다시 그려진다. */
@Composable
fun Labeled.label(): String = stringResource(labelRes)

/** 서비스·위젯처럼 [Context] 만 있는 곳에서 쓰는 라벨. */
fun Labeled.label(context: Context): String = context.getString(labelRes)

/** 뷰모델·리포지토리에서 쓰는 라벨. */
fun Labeled.label(strings: StringProvider): String = strings[labelRes]

/**
 * 저장된 용도 토큰의 리소스 id.
 *
 * 용도는 사용자가 자유롭게 추가하는 라벨이라 대부분 DB 값이 곧 표시 문구다.
 * 초기 버전에서 [Purpose] 이름으로 저장된 값만 다국어 리소스로 바꿔 준다.
 *
 * @param stored DB 에 저장된 용도 문자열
 * @return 기본 제공 용도면 리소스 id, 사용자 정의 라벨이면 null
 */
@StringRes
fun purposeLabelRes(stored: String): Int? =
    Purpose.entries.firstOrNull { it.name == stored }?.labelRes

/** 용도 라벨 — 컴포저블용. */
@Composable
fun purposeLabel(stored: String): String =
    purposeLabelRes(stored)?.let { stringResource(it) } ?: stored

/** 용도 라벨 — [Context] 만 있는 곳. */
fun purposeLabel(context: Context, stored: String): String =
    purposeLabelRes(stored)?.let { context.getString(it) } ?: stored

/** 용도 라벨 — 뷰모델·리포지토리. */
fun purposeLabel(strings: StringProvider, stored: String): String =
    purposeLabelRes(stored)?.let { strings[it] } ?: stored
