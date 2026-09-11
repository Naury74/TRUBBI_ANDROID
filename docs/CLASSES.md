# 클래스 지도

어디에 뭐가 있고 각각 무슨 일을 하는지. 처음 보는 사람이 고칠 곳을 찾는 용도.

- 파일 180개 / 공개 타입 약 190개
- 전체 나열이 아니라 **찾을 때 필요한 것만**. 세부는 각 파일 KDoc 참고
- 구조를 왜 이렇게 나눴는지는 [README 의 설계 메모](../README.md#설계-메모)

## 이럴 땐 여기

| 하려는 일 | 볼 곳 |
|---|---|
| 새 화면 추가 | `navigation/Routes.kt` → `navigation/TrubbiNavHost.kt` → `feature/<이름>/` |
| 기록 저장 규칙 변경 | `data/repo/RecordRepository.kt` |
| 여정 진행 규칙 변경 | `data/repo/JourneyRepository.kt` (단계 판정은 `advanceStop`) |
| 홈 화면 동작 변경 | `domain/journey/JourneyFlowController` (여정) / `feature/home/HomeLibraryController` (목록) |
| DB 스키마 변경 | `data/local/Entities.kt` 또는 `JourneyEntities.kt` → `TrubbiMigrations.kt` → `./gradlew test` |
| 새 문구 추가 | `app/src/main/res/values/strings.xml` + `values-en/` (아래 모듈에서 쓸 거면 그 모듈 res) |
| 음성 인식어 추가 | `core/voice/VoiceNlu.kt`(규칙) · `core/ml/SynonymDict.kt`(이미지 라벨) |
| 색·모양·간격 | `ui/theme/` · `ui/designsystem/` |
| 위젯·알림 | `widget/` |

## 모듈

```
:model → :core → :data → :app
```

아래가 위를 참조하지 않는다. 아래가 위의 일을 필요로 하면 인터페이스만 두고 구현을 위에서 주입.

---

## :model

플랫폼·저장소·화면을 모르는 타입. 다른 모듈이 전부 참조한다.

| 타입 | 역할 |
|---|---|
| `Labeled` | 화면 이름을 문자열 리소스 id 로 갖는 열거형 표식 |
| `RecordType` | 기록 종류 — 사진·영상·문서·바코드·메모·파일 |
| `Purpose` | 기본 제공 용도 (상차·하차·정비 등) |
| `StopKind` | 정차지 성격 — 출발지 / 상차 / 하차 |
| `StopPhase` | 정차지 단계 — 이동중 / 대기 / 작업 / 완료 |
| `JourneyEventType` | 타임라인 이벤트 종류. 제목 서식(`titleRes`)을 함께 갖는다 |
| `EvidenceCategory` | 증빙 분류 — 상차 전/중/후, 파손, 서류 등 |
| `ExpenseCategory` · `IssueType` | 비용 분류 · 이슈 종류 |
| `MissingEvidence` | 완료 전 확인에서 "빠졌다" 고 알릴 항목 |
| `MemoBlock` · `MemoContent` | 블록 단위 메모의 저장 형식 (JSON 인코딩/디코딩) |

## :core

안드로이드 SDK 와 외부 서비스 래퍼. 저장소도 화면도 모른다.

**위치**

| 타입 | 역할 |
|---|---|
| `LocationProvider` | 현재 위치·주소·장소 검색. Fused → LocationManager, 카카오 → Geocoder 순 폴백 |
| `CapturedLocation` · `PlaceCandidate` | 측위 결과 / 검색 후보 한 건 |
| `LocationKeys` | 카카오 REST 키. 앱이 빌드 설정에서 읽어 주입 (라이브러리가 앱 BuildConfig 를 안 보게) |

**음성**

| 타입 | 역할 |
|---|---|
| `SpeechInputState` | 마이크 상태. 클라우드 녹음 경로와 기기 인식 경로를 모두 감싼다 |
| `CloudStt` · `CloudTts` | Cloud Functions 를 거쳐 Google STT/TTS 호출 |
| `VoiceNlu` | 발화 → 의도. 클라우드(Claude) 우선, 실패 시 기기 내 규칙 |
| `VoiceIntent` · `VoiceAction` · `VoiceContext` | 해석 결과와 그때의 단계 맥락 |
| `VoicePrompter` | 안내 재생. 온라인·설정에 따라 클라우드/기기 음성 선택 |
| `VoicePreferences` | 음성 출력이 읽는 설정. 구현은 `:data` 의 `SettingsRepository` |

**파일 · 미디어**

| 타입 | 역할 |
|---|---|
| `MediaStorage` | 촬영본 저장, 썸네일, PDF 생성, 워터마크 굽기, 공유 uri 발급 |
| `SavedMedia` · `WatermarkStyle` | 저장 결과 / 워터마크 스타일(미리보기와 실제 굽기가 공유) |
| `BarcodeRenderer` | 저장된 바코드 값 → 다시 스캔 가능한 이미지 |

**분석**

| 타입 | 역할 |
|---|---|
| `MediaAnalyzer` | 사진 한 장의 OCR + 라벨링 (온디바이스) |
| `ReceiptParser` | 영수증 텍스트에서 금액·상호·비용 분류 추출 |
| `SynonymDict` | ML Kit 영어 라벨 → 한국어 검색어 확장 |

**그 외**

| 타입 | 역할 |
|---|---|
| `StringProvider` | 컴포저블 밖에서 문자열 리소스를 읽는 창구. 데이터·도메인이 `Context` 를 직접 안 보게 하는 경계 |
| `TrubbiLog` | 삼킨 실패 기록. 디버그 Logcat / 릴리스 Crashlytics, 사용자 데이터는 안 보냄 |
| `DateUtils` | 앱 전체가 같은 형식으로 날짜를 다루기 위한 모음 |
| `WeatherRepository` · `Weather` | Open-Meteo 현재 날씨 |
| `NotificationIcons` | 알림 큰 아이콘 비트맵 합성 |
| `core/i18n/Labels.kt` | `Labeled` → 문구. 컴포저블용 / `Context` 용 / `StringProvider` 용 |

## :data

저장 계층. 화면을 모르고 **사용자에게 보일 문구도 만들지 않는다.**

**Room**

| 타입 | 역할 |
|---|---|
| `TrubbiDatabase` | DB 정의. 버전은 `TrubbiMigrations.CURRENT_VERSION` 하나로 관리 |
| `TrubbiDao` | 기록·미디어·연락처 |
| `JourneyDao` | 여정·정차지·경로·비용·이슈·타임라인 |
| `RecordEntity` · `MediaEntity` · `RecordWithMedia` | 기록 한 건과 거기 붙은 파일들 |
| `JourneyEntity` · `JourneyStopEntity` · `JourneyWithStops` | 여정과 정차지 |
| `JourneyEventEntity` | 타임라인 한 줄. 제목은 종류 + 인자로 저장(번역 안 함) |
| `Converters` | 리스트·열거형 타입 컨버터 |
| `TrubbiMigrations` | 버전 상수, 파괴적 폴백 범위, `Migration` 목록 |
| `DatabaseModule` | Room 조립. 마이그레이션 정책이 저장 계층 밖으로 나가지 않게 여기 둔다 |

**리포지토리**

| 타입 | 역할 |
|---|---|
| `RecordRepository` | 기록 저장·조회 단일 창구. 저장 후 분석·위치 보강·여정 연결을 이어서 건다 |
| `JourneyRepository` | 여정의 모든 업무 규칙. 단계 판정(`advanceStop`), 완료 전 증빙 확인, 타임라인 기록 |
| `ContactsRepository` | 거래처·기사 연락처. 기기 주소록에서 가져온 항목은 원본 id 로 중복을 막는다 |
| `SettingsRepository` | DataStore 설정. `VoicePreferences` 구현도 겸한다 |
| `AuthRepository` | Firebase 로그인·계정. 이메일/구글/카카오/네이버 |

**백업**

| 타입 | 역할 |
|---|---|
| `BackupManager` | 기록·미디어·연락처를 ZIP 으로 내보내고 되돌린다. 결과는 숫자와 원인만(`BackupResult`) |
| `CloudBackupRepository` | 그 ZIP 을 Storage 에 올리고 메타를 Firestore 에 |
| `BackupScheduler` · `CloudBackupWorker` | 주기 백업을 WorkManager 에 걸고 실행 |

**화면 간 신호**

여러 화면·서비스가 한 값을 두고 오가야 하는 자리. 뷰모델에 두면 화면이 사라질 때 같이 사라진다.

| 타입 | 역할 |
|---|---|
| `JourneyControlSignal` | 앱 밖(알림·위젯·플로팅)에서 진행한 단계를 앱 안에 알린다 |
| `JourneyShareRequest` · `SharedPlaceParser` | 지도앱·메신저에서 공유된 장소를 받아 정차지 후보로 |
| `AlbumEntryRequest` | 홈에서 앨범 탭으로 넘길 때 미리 걸어 둘 필터 |
| `AmbientSignal` | 자동 테마가 참고하는 날씨 상태 |
| `RecordOrderHolder` | 앨범이 보여 주는 순서를 상세 화면과 공유 |
| `DataChangeNotifier` | 저장 후 바깥 표면(위젯) 갱신 알림. 구현은 `:app` 의 `WidgetUpdater` |

**표시 문구**

| 파일 | 역할 |
|---|---|
| `data/i18n/JourneyLabels.kt` | 정차지 이름, 여정 한 줄 요약, 타임라인 제목(`displayTitle`) |

## :app — domain

화면도 프레임워크도 모르는 규칙. 여기가 앱의 머리다.

**여정**

| 타입 | 역할 |
|---|---|
| `JourneyFlowController` | 진행 중 여정 하나의 상태와 단계 전환. 홈·플로팅·알림의 판단을 한 곳에 모은 곳 |
| `JourneyAnnouncer` | 안내 음성 문장 조합. 날씨·위치·시각을 전부 파라미터로 받아 무상태 |
| `JourneyVoice` | 음성 입출력 창구. 안내 on/off 판단을 여기서 한 번만 |
| `SendOff` · `ArrivalPrompt` · `StageConfirm` | 단계가 바뀐 순간 화면에 띄울 연출 데이터 |

**기록 목록**

| 타입 | 역할 |
|---|---|
| `HomeTimelineBuilder` | 필터·요약 규칙. 흐름도 코루틴도 안 써서 규칙만 검증 가능 |
| `RecordFilter` | 타임라인에 걸린 조건 전체(검색어·종류·용도·기간)를 한 덩이로 |
| `HomeState` · `HomeDateSection` · `TodaySummary` | 홈이 한 번에 그리는 데이터 |

**주행 · 리포트**

| 타입 | 역할 |
|---|---|
| `JourneyTracker` | 추적 서비스를 켜고 끄는 손잡이 |
| `JourneyTrackingService` | 여정 동안만 사는 포그라운드 서비스. 알림에서 단계 진행도 받는다 |
| `JourneyReportGenerator` · `Watermark` | 여정 리포트 A4 PDF, 공유용 워터마크 사진 |

## :app — feature

화면 단위. 각 폴더에 `Screen` + `ViewModel` + 화면 전용 상태 홀더.

| 폴더 | 화면 | 비고 |
|---|---|---|
| `home` | 홈 | `HomeViewModel` 은 컨트롤러 둘을 만들고 계정·날씨·인사말만 직접 |
| `journey` | 여정 상세·지도·지난 여정·시작 마법사 | 파일이 가장 많다. 아래 별도 설명 |
| `camera` | 촬영 (사진·영상·문서 스캔) | `DocumentScan` 은 ML Kit 문서 스캐너 연결 |
| `album` | 앨범 (필터·달력·검색) | |
| `detail` | 기록 상세 · 이머시브 뷰어 | `ImmersiveDetailScreen` 이 전체화면 좌우 넘김 |
| `editor` | 이미지 편집 · 영상 편집 | |
| `barcode` `memo` `contact` `calendar` `search` | 각 기능 화면 | |
| `account` | 로그인·가입·약관·계정 정보 | |
| `settings` | 설정 · 용도 관리 | |
| `share` | 공유 갤러리 (워터마크 지정) | |
| `onboarding` `viewer` `pdf` | 첫 실행 · 이미지 뷰어 · PDF 뷰어 | |

**`feature/journey` 안**

| 파일 | 역할 |
|---|---|
| `JourneyStartWizard` | 음성 마법사 화면(UI) |
| `JourneyWizardState` | 그 마법사의 대화 흐름 상태머신. 화면과 분리한 이유는 다루는 게 배치가 아니라 흐름이라서 |
| `WizardVoicePanels` · `WizardPlacePickers` · `WizardChrome` | 마법사 화면 조각 |
| `ActiveJourneyCard` | 홈 상단 진행 중 여정 카드 |
| `JourneyStopTrack` | 정차지 진행 표시 — 가로 시퀀스 / 세로 스테퍼 |
| `JourneyOverlays` | 단계 전환 순간의 전체화면 연출 |
| `ArrivalPromptSheet` | 도착 직후 "바로 시작할까요" 물음 (음성 응답 포함) |
| `JourneyDetailScreen` · `JourneyDetailTabs` · `JourneyDetailRows` | 여정 상세 화면 / 탭 본문 / 목록 행 |
| `JourneyExpenseUi` | 비용·이슈 입력 시트 |
| `StopReorderSheet` | 정차지 순서 변경 (음성 지원) |

## :app — ui · navigation · widget

| 폴더 | 내용 |
|---|---|
| `ui/theme` | 색 토큰, 타이포, 모양, `TrubbiColors` |
| `ui/designsystem` | `GlassComponents`(유리 표면), `AppIcons`, `TrubbiMotion`, `QuickDock`, `TrubbiDialog`, `TrubbiToast`, `DragReorder` |
| `ui/common` | `FeedCells`, `RecordCard`, `TrubbiCalendar`, `KakaoMapView`, `MemoBlocksView` 등 공용 조각 |
| `ui/weather` | 날씨 배경·헤더 |
| `navigation` | `Routes`(경로 문자열), `TrubbiNavHost`, `GlassTabBar` |
| `widget` | 위젯 4종, `FloatingLauncherService`(플로팅 버블), `QuickNotifier`(상주 알림), `WidgetUpdater` |

---

## 흐름

코드를 따라갈 때 시작점.

### 사진을 찍으면

```
CameraScreen
  → CameraViewModel.onPhotoCaptured()
  → MediaStorage.decodeNormalized() → saveBitmap()
  → RecordRepository.createPhotoRecord()
      ├─ dao.insertRecord() / insertMedia()
      ├─ triggerAnalysis()        OCR·라벨 → 검색 색인 (백그라운드)
      └─ JourneyRepository.onRecordSaved()   진행 중 여정이 있으면 정차지에 연결
  → CameraViewModel: attachLocationAsync()   좌표·주소 보강 (백그라운드)
```

저장 자체는 먼저 끝내고 분석·측위는 뒤에 붙인다. 터널에서 위치가 안 잡혀도 기록은 남는다.

### 정차지 단계가 올라가면

진입점이 넷이고, 전부 같은 함수 하나로 들어간다.

```
홈 카드      → JourneyFlowController.advanceCurrentStop()
여정 상세    → JourneyDetailViewModel.advance()
알림·플로팅  → JourneyTrackingService (ACTION_ADVANCE)
바탕화면 위젯 → JourneyWidget
        ↓
   JourneyRepository.advanceStop(stop)
        ├─ 시각 컬럼만 갱신 (arrivedAt / workStartedAt / completedAt)
        ├─ event()  타임라인에 종류 + 인자 기록
        └─ DataChangeNotifier.journeyChanged()  → 위젯 갱신
        ↓
   JourneyControlSignal.advance   앱 밖에서 진행했으면 앱 안에도 알림
        ↓
   JourneyFlowController 가 구독 → 같은 확인 화면 표시
```

상태를 저장하지 않고 시각에서 계산하므로, 어느 입구로 눌러도 결과가 갈라지지 않는다.

### 음성으로 여정을 만들면

```
JourneyStartWizard (화면)
  → SpeechInputState.start()          마이크
  → JourneyWizardState.onSpoken()     받아쓴 문장
  → JourneyVoice.interpret()          → VoiceNlu (클라우드 → 기기 규칙 폴백)
  → 의도에 따라 분기
       PLACE   → searchPlaces() → 후보 목록 → onPickSpoken() → pick()
       CURRENT → useHere()
       SKIP / BACK / CANCEL / MOVE …
  → 확정되면 callbacks().onStart(drafts)
  → HomeViewModel.journey.startJourney()
  → JourneyRepository.startJourney() + JourneyTracker.start()
```

단계 이동·정정·건너뛰기가 얽히는 곳이라 `JourneyWizardStateTest` 가 전이를 고정한다.

### 백업하면

```
설정 화면 → SettingsViewModel.exportBackup(uri)
  → BackupManager.export()      기록·미디어·연락처 → ZIP
  → BackupResult.Exported(records, media)     숫자만 돌려준다
  → SettingsViewModel.describe()              문구는 화면이 만든다

클라우드: CloudBackupRepository → Storage 업로드 + Firestore 메타
자동:    BackupScheduler → CloudBackupWorker (WorkManager, Wi-Fi 조건)
```

## 이름 규칙

| 접미사 | 무엇 |
|---|---|
| `~Screen` | 화면 진입 컴포저블 |
| `~ViewModel` | 화면 상태 |
| `~Controller` | 화면 수명에 묶인 상태 + 동작 묶음. `@AssistedInject` 로 `viewModelScope` 를 받는다 |
| `~State` | 상태만 들고 있는 홀더 (코루틴 없음) |
| `~Builder` · `~Announcer` · `~Parser` | 무상태. 입력을 받아 결과만 돌려준다 |
| `~Repository` | 저장·조회 창구 |
| `~Entity` · `~Dao` | Room |
| `~Signal` · `~Request` · `~Holder` | 화면 간에 값을 넘기는 앱 스코프 객체 |
