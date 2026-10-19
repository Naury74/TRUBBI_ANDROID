# ADR-0001: AGP 9 전환에 따른 정적 분석 구성 변경

- 상태: 채택
- 날짜: 2026-10-19

## 배경

Gradle 8.13에서 9.8.1로, AGP 8.13에서 9.4.1로, Kotlin 2.0.21에서 2.4.21로 올렸다. Android Studio에 들어 있는 JDK가 25로 바뀌면서 Gradle 8.13으로는 프로젝트를 열 수 없었기 때문이다.

## 결정

### detekt를 Gradle 플러그인 대신 CLI로 실행한다

- detekt 1.23 Gradle 플러그인은 AGP 9의 내장 Kotlin을 알아보지 못해 `detektDebug` 같은 변형별 작업을 만들지 않는다.
- detekt 1.23은 자기가 빌드된 Kotlin 2.0.21 컴파일러로만 동작하고, 플러그인 경로로 실행하면 JDK 25에서 실패한다.
- 그래서 루트 `build.gradle.kts`에서 `detekt-cli`를 `JavaExec`로 실행하고, detekt 설정의 Kotlin 의존성은 2.0.21로 고정한다. PulseDevelopmentKitSDK와 같은 방식이다.
- 명령은 `./gradlew detektDebug`에서 `./gradlew detekt`로 바뀐다. 타입 해석을 쓰는 규칙은 돌지 않는다. detekt 2.0이 안정판이 되면 플러그인으로 되돌리는 것을 다시 검토한다.

### ktlint 1.8에서 새로 생긴 규칙 두 개를 끈다

| 규칙 | 끈 이유 |
| --- | --- |
| `standard:kdoc` | 파일 첫머리에 두는 설명 주석(`/** */`)을 위치가 잘못된 KDoc으로 본다. 이 저장소는 파일 단위 설명에 이 형식을 쓴다(33곳). |
| `standard:blank-line-between-when-conditions` | 여러 줄 분기가 하나라도 있으면 모든 `when` 분기 사이에 빈 줄을 요구한다(190곳). Compose 화면의 상태 분기가 길어져 읽기 나빠진다. |

다시 켜려면 위 형식을 일괄로 바꾸는 커밋을 따로 만든 뒤 `.editorconfig`에서 두 줄을 지운다.

## 결과

- `./gradlew ktlintCheck detekt :app:lintDebug test assembleDebug`가 JDK 25에서 통과한다.
- AGP 9부터 단위 테스트는 기본으로 debug 변형에서만 돈다. 같은 테스트를 release에서 한 번 더 돌리던 것이 빠질 뿐, 테스트 수(127개)는 그대로다.
