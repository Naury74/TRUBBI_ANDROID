# TRUBBI

화물차 기사용 업무 기록 앱. 사진·영수증·바코드를 여정 단위로 묶어 두고 PDF 리포트로 뽑음.

- Kotlin / Compose / Hilt / Room / Coroutines
- minSdk 30, targetSdk 36
- 멀티모듈 (`:model` `:core` `:data` `:app`)
- 클래스 지도 · 주요 흐름 → [docs/CLASSES.md](docs/CLASSES.md)

## 왜 만들었나

- 화물 일은 증빙이 근거의 전부 — 파손 여부, 하차 시각, 대기 시간
- 그런데 사진이 갤러리에 수천 장으로 쌓임
- 두 달 전 파손 사진을 찾아도 "그날 그 건" 이라는 걸 증명할 방법이 없음
- 날짜만으로는 하루에 세 곳 도는 날을 구분 못 함

→ 사진 한 장을 **언제·어디서·무슨 작업 중에** 찍었는지와 함께 묶는다.

## 기능

**여정**

- 여정 시작 → 정차지별 도착 / 작업 시작 / 완료 기록
- 그 사이 촬영한 사진은 해당 정차지 증빙으로 자동 연결
- 정차지 추가·순서 변경·현재 위치로 정차지 만들기
- 완료 전 빠진 증빙 확인
- 주행 경로 기록 (포그라운드 서비스)
- 여정 리포트 PDF · 워터마크 사진 일괄 공유

**음성** (운전 중 사용)

- 음성으로 여정 등록 — 목적지 → 출발지 → 경유지까지 대화로 진행
- 정정("아니 인천항"), 순번 선택("두 번째"), 건너뛰기, 취소
- 클라우드 STT/NLU 우선, 오프라인이면 기기 인식 + 규칙 기반으로 폴백
- 단계 진행 안내 TTS

**기록**

- 사진 / 영상 / 문서 스캔 / 바코드 / 메모
- 영수증 OCR — 금액·상호·분류 자동 추출 (온디바이스)
- 바코드 스캔 및 생성
- 이미지 편집(자르기·회전·밝기), 영상 자르기
- 종류·용도·기간 필터, 전문 검색, 달력 뷰
- 워터마크(시각·좌표·용도) 새겨 공유

**앱 밖**

- 홈 위젯 4종 — 여정 카드, 지도, 빠른 촬영, 바코드
- 화면 위 플로팅 버튼
- 알림에서 바로 단계 진행
- 지도앱·메신저에서 공유한 장소를 정차지로 받기

**기타**

- 로컬 우선 — 로그인 없이 전부 동작
- Firebase 는 백업·동기화용 (선택)
- 한국어 / 영어

## 빌드

```bash
# 카카오 키를 local.properties 에 (없어도 빌드됨, Geocoder 폴백)
echo "KAKAO_REST_KEY=..."   >> local.properties
echo "KAKAO_NATIVE_KEY=..." >> local.properties

# Firebase 설정 — 저장소에 없다(공개 저장소라 키를 올리지 않음).
# Firebase 콘솔 > 프로젝트 설정 > Android 앱에서 google-services.json 을 받아 app/ 에 둔다.
# 없으면 가짜 값으로라도 빌드하려면: cp app/google-services.example.json app/google-services.json
# Cloud Functions 배포용 .firebaserc 도 .firebaserc.example 을 복사해 프로젝트 ID 를 넣는다.

# AdMob 실제 ID (release 전용, 없으면 광고를 띄우지 않음. debug 는 Google 테스트 ID)
echo "ADMOB_APP_ID=ca-app-pub-...~..."   >> local.properties
echo "ADMOB_NATIVE_UNIT=ca-app-pub-.../..." >> local.properties
echo "ADMOB_BANNER_UNIT=ca-app-pub-.../..." >> local.properties

./gradlew ktlintFormat              # 정렬
./gradlew ktlintCheck detekt        # 스타일 + 정적 분석
./gradlew test                      # 전 모듈 단위 테스트
./gradlew assembleDebug
```

**배포는 AAB**

- ML Kit·카카오맵 네이티브 라이브러리가 ABI 당 40MB 초과
- 4개 ABI 를 한 APK 에 담으면 164MB

```bash
./gradlew bundleRelease                            # 79MB → Play 가 기기당 약 50MB 로 배달
./gradlew assembleRelease -Ptrubbi.abiSplits=true   # 직접 배포용 (arm64 67MB)
```

**서명**

- `local.properties` 또는 환경 변수에서 읽음
- `TRUBBI_KEYSTORE` / `TRUBBI_KEYSTORE_PASSWORD` / `TRUBBI_KEY_ALIAS` / `TRUBBI_KEY_PASSWORD`
- 하나라도 없으면 서명 설정을 만들지 않음 → 키 없이도 릴리스 빌드는 통과

**참고**

- 린터 규칙: `.editorconfig`(ktlint), `config/detekt.yml`(detekt). 적용은 루트 `subprojects` 에서 한 번
- 미리보기는 `ui/designsystem/DesignSystemPreviews.kt` 에 상태 없는 조각만 (화면 전체는 Hilt·권한 때문에 안 뜸)
- 스튜디오와 터미널에서 동시에 빌드하면 `R.jar` 충돌 → `./gradlew --stop` 후 재시도

## 구조

```
:model   열거형·여정 모델·메모 저장 형식. 의존 없음
   ↑
:core    안드로이드 SDK·외부 서비스 래퍼 (위치·음성·ML·파일·날씨·바코드·로깅)
   ↑
:data    Room, 리포지토리, 설정, 인증, 백업, 화면 간 신호
   ↑
:app     도메인 규칙 + 화면
```

```
app/src/main/java/com/naury/trubbi/
├─ domain/        journey(여정 상태머신) · record(홈 목록) · tracking(경로) · report(PDF)
├─ feature/       화면 단위 — Screen + ViewModel + 화면 전용 상태 홀더
├─ ui/            디자인 시스템, 공용 컴포넌트, 테마
├─ navigation/    경로와 NavHost
└─ widget/        위젯, 플로팅, 알림
```

**각 클래스가 뭘 하는지, 어느 파일을 고치면 되는지는 [docs/CLASSES.md](docs/CLASSES.md).**
주요 흐름(사진 저장 / 단계 진행 / 음성 여정 등록 / 백업)의 호출 경로도 거기 있다.

## 설계 메모

### 모듈을 가른 기준

- 기준은 "레이어라서" 가 아니라 **컴파일러가 막아 줄 위반이 실제로 있는가**
- 패키지로만 나눴을 때는 빌드가 방향을 검사하지 않아 어긋나도 안 드러남
- `import` 를 세어 보니 `data` → 위젯, `core` → 화면 문구 참조가 있었음
- 모듈로 가르자 컴파일 오류가 됨

아래가 위의 일을 필요로 하면 인터페이스만 두고 구현은 위에서 주입.

- `DataChangeNotifier` — `:data` 가 저장 후 위젯 갱신을 알리되 위젯 클래스는 모름
- `VoicePreferences` — `:core` 음성 출력이 설정값을 읽되 저장소는 모름

`domain` 은 모듈로 떼지 않음. `feature` 와 같은 모듈에 있어도 의존 방향이 안 어긋나서
막을 위반이 없고, 그런 경계는 빌드 설정만 늘어남.

### 진행 상태

- 정차지 상태(이동중/대기/작업/완료)를 컬럼으로 저장하지 않음
- 도착·작업시작·완료 시각에서 계산

**이유**

- 같은 여정을 건드리는 입구가 4개 (홈 카드 / 알림 / 위젯 / 플로팅)
- 상태 + 시각을 같이 저장 → 하나만 어긋나면 두 값이 달라짐
- 시각만 저장해서 진실을 한 곳에 둠

**단계 진행**

- `JourneyRepository.advanceStop()` 단일 진입점
- 화면 스냅샷 아닌 DB 재조회로 판단
- 연타·동시 클릭에도 단계 역행 없음 (`JourneyRepositoryTest`)

### 홈 뷰모델 분해

홈이 여정 진행과 기록 목록을 함께 다루지만 두 흐름이 서로의 상태를 안 읽음.
리포지토리 16개 물고 910줄이던 `HomeViewModel` 을 아래로 갈랐음.

- `JourneyFlowController` — 진행 중 여정의 상태와 단계 전환 (뷰모델 스코프)
- `HomeLibraryController` — 목록·필터·다중 선택·공유 (뷰모델 스코프)
- `JourneyAnnouncer` — 안내 문구 조합 (무상태)
- `HomeTimelineBuilder` — 목록 필터·요약 규칙 (무상태)
- `HomeSelectionState` — 다중 선택 전이 (무상태)
- `HomeViewModel` — 위 둘을 화면 수명에 맞춰 생성 + 계정·날씨·인사말

**스코프**

- 두 컨트롤러는 흐름 구독이 필요 → 스코프 필요
- 스코프를 스스로 만들면 화면이 사라져도 살아남음
- `@AssistedInject` 로 `viewModelScope` 를 받아 화면과 함께 정리

**무상태 둘**

- 날씨·위치·시각을 전부 파라미터로 받음 → 기기 상태 없이 규칙만 검증 가능
- 그래서 테스트의 절반 가까이가 이 둘에 붙어 있음

**단계 전환 판단을 한 곳으로**

- 홈 카드·플로팅·알림이 각자 화면 분기를 갖고 있었음
- 지금은 셋 다 리포지토리가 돌려준 단계만 보고, 화면 문구로 옮기는 건 `JourneyFlowController` 만

### 모듈 경계에서 드러난 것

모듈을 나누면 리소스도 내려감. AGP 8 `nonTransitiveRClass` 때문에 위 모듈 `R` 에서 사라지고
39곳이 깨졌는데, 전부 앱이 도메인 타입 라벨을 문자열 이름으로 직접 부르던 자리였음.

```kotlin
stringResource(R.string.record_type_photo)   // 라벨 바꾸려면 열거형과 화면을 따로 고쳐야 함
stringResource(RecordType.PHOTO.labelRes)    // 타입이 이미 답을 갖고 있음
```

`:data` 를 가를 때는 앱과 공유하는 문자열이 9개. 공유가 아니라 **저장 계층이 사용자 문구를
만들고 있는 것**이 문제였음.

- `JourneyRepository` — 타임라인 제목을 **번역된 문자열로 DB 에 저장** → 종류 + 인자만 저장
- `BackupManager` — `Error("백업 실패: …")` → 숫자와 원인만, 문구는 설정 화면이
- `ContactsRepository` — 빈 이름에 "이름 없음" 채워 저장 → 빈 값 그대로, 화면이 대체 문구

첫 번째는 실제 버그. 저장 시점 언어로 문장을 넣어서 앱 언어를 영어로 바꿔도 지난 타임라인은
한국어로 남음.

- 이벤트를 `JourneyEventType` + 인자로 저장하도록 변경, 스키마 7
- 기존 행은 번역 전 원본이 없어 복원 불가 → 그대로 두고, 인자가 비면 저장된 문구 사용
- `MIGRATION_6_7` 은 컬럼만 추가

`nonTransitiveRClass=false` 를 켜면 덮고 넘어갈 수 있었지만 경계가 드러낸 걸 도로 가리는
선택이라 안 함. 표현 책임을 화면으로 올리니 공유 문자열이 0개가 됨.

## 테스트

전부 붙이는 대신 **틀어지면 앱 전체가 어긋나는 곳**만. 화면 그리는 코드에는 거의 안 붙임
(눈으로 보는 게 빠르고, 레이아웃은 자주 바뀌어 테스트가 먼저 낡음).

| 테스트 | 왜 여기인가 |
|---|---|
| `JourneyWizardStateTest` | 인식 → 해석 → 검색 → 후보 확인 → 확정에 단계 이동·정정·건너뛰기가 얽힌 곳. 잘못 끼면 정차지 순서가 뒤집힘 |
| `JourneyRepositoryTest` | 도착 → 작업 → 완료 사이클, 완료 전 증빙 확인. 인메모리 Room 위에서 실제 리포지토리 |
| `TrubbiMigrationsTest` | 버전만 올리고 마이그레이션을 빠뜨린 상태. 개발 기기에선 안 드러나고 사용자 업데이트 때 터짐 |
| `JourneyEventTitleTest` | 타임라인 제목 조립 규칙과 버전 7 이전 행 폴백 |
| `HomeTimelineBuilderTest` | 필터는 타임라인에만, 오늘 요약은 전체 기준 유지 |
| `JourneyAnnouncerTest` | 값 없는 조각만 빠지는지, 날씨가 시간대보다 먼저 걸리는지 |
| `HomeSelectionStateTest` | 기록 단위와 미디어 단위가 겹치는 다중 선택 전이 |
| `JourneyStopStateTest` | 시각에서 단계를 계산하는 규칙 |
| `SettingsViewModelTest` | 백업 진행 표시가 성공·실패 어느 쪽으로 끝나든 해제되는지 |
| `AlbumViewModelTest` | 종류·용도·기간 필터가 `combine` 으로 합쳐질 때의 교집합 |
| `VoiceNluLocalTest` | 네트워크 없이 도는 음성 의도 해석 |
| `ReceiptParserTest` | 영수증 OCR 에서 금액·상호·분류 뽑기 |
| `SharedPlaceParserTest` | 지도앱 공유 텍스트 파싱 |

## 다국어

- 기본 한국어(`values/`), 영어(`values-en/`)
- 안드로이드 13+ 는 설정 → 앱 언어로 개별 전환 (`res/xml/locales_config.xml`)
- 열거형은 문구가 아니라 리소스 id 만 가짐 (`Labeled`)
- 해석은 확장 함수 한 곳 — 컴포저블용 / `Context` 용 / 뷰모델용
- 도메인 타입은 `core/i18n`, 여정·정차지 엔티티는 `data/i18n`

**번역하지 않는 것**

- 음성 인식 사전(`VoiceNlu`, `SynonymDict`, `ReceiptParser`)의 한국어 낱말은 리소스가 아니라
  도메인 데이터. 한국어 발화·영수증을 알아듣기 위한 규칙이라 코드에 둠
- 즉 화면·안내는 영어로 바뀌지만 받아쓰기·의도 해석은 아직 한국어 전용
- 약관·개인정보 처리방침 본문도 한국어만. 번역하면 법적 효력이 있는 문장이 달라짐

## 관측 · 접근성

- `runCatching` 으로 삼킨 실패 중 **사용자가 결과물을 잃는 것**만 `TrubbiLog` 로 기록
- 디버그는 Logcat, 릴리스는 Crashlytics
- 사진·주소·연락처는 안 보냄 — 어느 동작이 실패했는지와 예외 종류만

**전부 로깅하지 않는 이유**

- 측위는 새 측위 → 마지막 위치 → `LocationManager` 폴백 사슬이라 단계마다 삼켜야 함
- 날씨는 없으면 그 부분만 빠지면 되는 부가 정보
- 그런 자리에는 왜 삼키는지 주석으로 적어 둠

**접근성**

- 아이콘만 있는 버튼은 `contentDescription` 을 필수 파라미터로 (기본값 주면 빠뜨려도 빌드 통과)
- 상태에 따라 동작이 바뀌는 버튼은 현재 상태가 아니라 **누르면 일어날 일**을 읽음

## TODO

- [ ] 영상 워터마크 — 사진엔 시각·좌표를 새기는데 영상은 원본 그대로 공유됨. 다음 순위
- [ ] 계측 테스트 — 카메라·지도·위젯은 실기기에서만 확인됨 (에뮬레이터는 카카오맵·카메라가 제대로 안 떠서 반쪽)
- [ ] Baseline Profile 실측 — 지금은 시작 경로를 코드로 따라가 손으로 적은 것. `androidx.benchmark` 를 쓰려면 위의 계측 환경이 먼저
- [ ] ML Kit → Play Services 변형 — 40MB 줄지만 최초 실행에 모델 다운로드 필요. 오프라인 우선과 맞바꿔야 해서 보류
- [ ] 주기 백업 기본값 — `BackupScheduler` 에 뼈대만. Wi-Fi 조건(`NetworkType.UNMETERED`)은 넣었지만 사진 데이터 요금 때문에 기본 ON 여부는 실사용 후 결정
- [ ] 예외 메시지 타입화 — `AuthRepository`·`BackupManager` 가 번역된 문구를 담아 예외를 던짐. 저장은 안 해서 버그는 아니지만 표현 책임이 아래에 남음
- [ ] 영어 화면 검수 — 리소스는 다 채웠지만 한국어 기준 폭이라 잘리는 곳이 있을 것
