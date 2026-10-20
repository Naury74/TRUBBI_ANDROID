/**
 * TRUBBI 소셜 로그인용 Cloud Functions (카카오 / 네이버 → Firebase Custom Token).
 *
 * 배포 전 준비 (Blaze 요금제 필요):
 *   firebase init functions      // JavaScript 선택, 이 index.js 로 교체
 *   firebase functions:secrets:set KAKAO_ADMIN_KEY     // 카카오 개발자 > 앱 > 보안 > Admin 키 (선택)
 *   // 네이버는 토큰으로 사용자 조회만 하므로 비밀키 불필요
 *   firebase deploy --only functions
 *
 * 앱은 이 함수들을 FirebaseFunctions 로 호출 → 반환된 customToken 으로 signInWithCustomToken.
 */
const functions = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const admin = require("firebase-admin");
const fetch = require("node-fetch"); // package.json 에 "node-fetch": "^2" 추가
const textToSpeech = require("@google-cloud/text-to-speech"); // npm i @google-cloud/text-to-speech
const speech = require("@google-cloud/speech"); // npm i @google-cloud/speech
const Anthropic = require("@anthropic-ai/sdk"); // npm i @anthropic-ai/sdk

admin.initializeApp();
const db = admin.firestore();
const ttsClient = new textToSpeech.TextToSpeechClient();
const speechClient = new speech.SpeechClient();

// Anthropic 키는 앱이 아니라 함수에만 존재(보안). 배포 전:
//   firebase functions:secrets:set ANTHROPIC_API_KEY
const ANTHROPIC_API_KEY = defineSecret("ANTHROPIC_API_KEY");

/**
 * 고품질 한국어 TTS (Google Cloud Text-to-Speech). 로그인 사용자만 호출 가능(남용/과금 방지).
 * 앱에서 data: { text, voice? } 로 호출 → { audio: base64(mp3) } 반환.
 * 사전 준비: GCP 콘솔에서 "Cloud Text-to-Speech API" 사용 설정.
 */
exports.synthesizeSpeech = functions.onCall(async (request) => {
  if (!request.auth) throw new functions.HttpsError("unauthenticated", "로그인이 필요해요");
  const text = ((request.data && request.data.text) || "").toString().trim().slice(0, 400);
  if (!text) throw new functions.HttpsError("invalid-argument", "text 필요");
  // 앱이 보낸 목소리 이름은 한국어 목소리만 받는다(임의 목소리로 과금 단가가 높은 모델을 부르지 못하게).
  const requested = ((request.data && request.data.voice) || "").toString();
  const voiceName = /^ko-KR-[A-Za-z0-9-]{1,40}$/.test(requested) ? requested : "ko-KR-Chirp3-HD-Aoede";

  // Chirp3-HD voices don't accept pitch; keep audioConfig minimal. Fall back to Neural2 if the
  // requested voice isn't available so the client still gets cloud audio.
  async function synth(name) {
    const [resp] = await ttsClient.synthesizeSpeech({
      input: { text },
      voice: { languageCode: "ko-KR", name },
      audioConfig: { audioEncoding: "MP3", speakingRate: 1.0 },
    });
    return resp.audioContent;
  }

  let audioContent;
  try {
    audioContent = await synth(voiceName);
  } catch (e) {
    console.warn("voice", voiceName, "failed, falling back to Neural2:", e.message);
    audioContent = await synth("ko-KR-Neural2-A");
  }
  return { audio: Buffer.from(audioContent).toString("base64") };
});

/** provider_links/{provider}:{providerUserId} 로 uid 를 찾거나 새로 만든다. */
async function resolveUid(provider, providerUserId, profile) {
  const linkRef = db.collection("provider_links").doc(`${provider}:${providerUserId}`);
  const snap = await linkRef.get();
  if (snap.exists) return snap.data().uid;

  // 신규 — Firebase Auth 사용자 생성 후 매핑 저장.
  const userRecord = await admin.auth().createUser({
    displayName: profile.name || undefined,
    photoURL: profile.photo || undefined,
  });
  const uid = userRecord.uid;
  const now = Date.now();
  await linkRef.set({ provider, providerUserId: String(providerUserId), uid, email: profile.email || null, linkedAt: now });
  await db.collection("users").doc(uid).set({
    uid, email: profile.email || null, displayName: profile.name || null,
    photoUrl: profile.photo || null, providers: [provider], primaryProvider: provider,
    status: "active", createdAt: now, updatedAt: now, lastLoginAt: now,
  }, { merge: true });
  return uid;
}

/**
 * 회원가입용 이메일 중복 확인. 앱에서 data: { email } 로 호출 → { exists: true/false }.
 * Admin SDK라 '이메일 열거 보호'와 무관하게 정확히 판별 (입력 단계에서 즉시 안내용).
 */
exports.checkEmail = functions.onCall(async (request) => {
  const email = ((request.data && request.data.email) || "").toString().trim().toLowerCase();
  if (!email) throw new functions.HttpsError("invalid-argument", "email 필요");
  try {
    const u = await admin.auth().getUserByEmail(email);
    return { exists: true, displayName: u.displayName || null, photoUrl: u.photoURL || null };
  } catch (e) {
    if (e && e.code === "auth/user-not-found") return { exists: false };
    throw new functions.HttpsError("internal", "확인 실패");
  }
});

/**
 * 고정밀 한국어 받아쓰기 (Google Cloud Speech-to-Text). 로그인 사용자만.
 * 앱에서 data: { audio: base64(LINEAR16 16kHz mono PCM), sampleRate?, hints?: string[] } 로 호출.
 * hints 는 지금 화면에 있는 정차지 이름처럼 나올 법한 말이다. 고유명사 인식률을 크게 올린다.
 * → { transcript, alternatives:[...] }. 사전 준비: GCP에서 "Cloud Speech-to-Text API" 사용 설정.
 */
exports.transcribeSpeech = functions.onCall(async (request) => {
  if (!request.auth) throw new functions.HttpsError("unauthenticated", "로그인이 필요해요");
  const audio = (request.data && request.data.audio) || "";
  if (!audio) throw new functions.HttpsError("invalid-argument", "audio 필요");
  // 16kHz 모노 16bit 기준 15초 ≈ 480KB → base64 ≈ 640KB. 그보다 크면 앱이 보낼 수 없는 길이라 거절한다.
  if (audio.length > 700000) throw new functions.HttpsError("invalid-argument", "녹음이 너무 길어요");
  const sampleRate = Number((request.data && request.data.sampleRate) || 16000) || 16000;
  // 앱이 보낸 힌트. 개수와 길이를 제한해 남용을 막는다(Cloud STT 한도: 문구 100자).
  const hints = (Array.isArray(request.data && request.data.hints) ? request.data.hints : [])
    .filter((h) => typeof h === "string")
    .map((h) => h.trim().slice(0, 100))
    .filter(Boolean)
    .slice(0, 60);
  try {
    const [resp] = await speechClient.recognize({
      config: {
        encoding: "LINEAR16",
        sampleRateHertz: sampleRate,
        languageCode: "ko-KR",
        maxAlternatives: 5,
        model: "latest_short",
        useEnhanced: true,
        enableAutomaticPunctuation: false,
        // 인식률 향상용 음성 적응 힌트. 순번/명령어는 오인식이 잦아 강하게(boost↑) 준다.
        speechContexts: [
          { phrases: ["첫번째", "두번째", "세번째", "네번째", "다섯번째", "여섯번째", "일곱번째", "마지막", "이거", "그거", "맞아", "다시"], boost: 16 },
          { phrases: ["여정 시작", "출발하자", "취소", "현재 위치", "경유지 추가", "상차지 추가", "하차지 추가", "없어요"], boost: 12 },
          { phrases: ["상차", "하차", "물류센터", "신항", "터미널", "공장", "창고", "휴게소"], boost: 8 },
          ...(hints.length ? [{ phrases: hints, boost: 14 }] : []),
        ],
      },
      audio: { content: audio },
    });
    const alts = ((resp.results && resp.results[0] && resp.results[0].alternatives) || [])
      .map((a) => (a.transcript || "").trim())
      .filter(Boolean);
    return { transcript: alts[0] || "", alternatives: alts };
  } catch (e) {
    console.error("transcribeSpeech", e && e.message);
    throw new functions.HttpsError("internal", "받아쓰기 실패");
  }
});

/** 구조화 의도 출력 도구 — Claude가 이 스키마로만 답하도록 강제(tool_choice). */
const INTENT_TOOL = {
  name: "emit_intent",
  description: "트럭 기사 여정 음성 비서: 사용자 한국어 발화를 구조화된 의도로 변환한다.",
  input_schema: {
    type: "object",
    properties: {
      action: {
        type: "string",
        enum: ["place", "select", "correct", "current", "add_stop", "skip", "start", "cancel", "back", "repeat", "move", "none"],
        description:
          "place=장소를 말함(검색), select=후보 중 N번째/이거 선택, correct=직전 결과를 부정·정정('아니 그거 말고…'), current=현재 위치 사용, add_stop=경유지 추가, skip=나중에/건너뛰기, start=여정 시작/추가 완료, cancel=취소, back=이전 단계, repeat=다시 말해달라/안내 반복, move=정차지 순서 이동. '○○ 먼저'/'○○ 가는 걸로'/'○○ 부터'면 place=○○. 'A 가기 전에 B로 가야해'처럼 'B를 먼저'라는 뜻이면 place=B(앞으로 옮길 정차지)로 준다. none=의미 없음",
      },
      place: { type: ["string", "null"], description: "검색·이동할 정규화된 장소명(군더더기·조사·명령어 제거). 없으면 null" },
      ordinal: { type: ["integer", "null"], description: "후보 목록에서 고른 1-기반 순번. 없으면 null" },
      kind: { type: ["string", "null"], enum: ["pickup", "dropoff", null], description: "상차(pickup)/하차(dropoff) 의도. 불명확하면 null" },
      target: { type: ["string", "null"], enum: ["place", "ordinal", "step", null], description: "correct일 때 무엇을 고치는지: 장소/순번/단계" },
      reply: { type: ["string", "null"], description: "사용자에게 들려줄 1문장 확인·되묻기 멘트(선택)" },
      confidence: { type: "number", description: "0~1 확신도" },
    },
    required: ["action", "confidence"],
  },
};

const INTENT_SYSTEM = [
  "너는 트럭 기사용 한국어 음성 비서다. 운전 중 시끄러운 환경이라 받아쓰기에 오타·동음이의·띄어쓰기 오류가 섞인다. 문맥으로 적극 추론하고, 애매하면 가장 그럴듯한 장소명을 골라 place로 정규화하라.",
  "여정 만들기 단계(step): dest=목적지(하차지), origin=출발지(상차지), extra=경유지 추가 여부, pick=화면의 후보 목록에서 고르는 중, origin_yesno='현재 위치를 출발지로 쓸까요?'에 답하는 중.",
  "입력 JSON의 candidates는 현재 화면 후보들(순서대로 1번,2번…), decided는 이미 확정한 곳들, lastPrompt는 직전 비서 멘트다.",
  "정정/부정 폭넓게 인식: '아니', '아니아니', '그거 말고', '그게 아니라', '틀렸어', '잘못 들었어', '다시', '다른 거', '딴 데', '그 옆에', '비슷한데 ~', '두번째 말고 세번째' → action=correct. 새 장소가 들리면 place, 새 순번이면 ordinal, 그냥 다시 듣기면 target=step.",
  "★ pick 단계에서 순번이 아니라 '장소를 다시 말하면'(예: 후보가 틀려서 '부산 신항 말고 부산 북항', 또는 그냥 '평택항') → action=place(또는 correct)+place 로 재검색하게 하라. 절대 그 말을 select 로 억지 매핑하지 마라.",
  "장소 정규화: 군더더기·조사·명령어 제거. 예) '어 그 평택 물류센터 쪽으로 가주세요'→place='평택 물류센터', '음 일단 부산신항이요'→place='부산 신항'. 숫자만/한두 글자면 place로 보지 말고 ordinal·명령으로 해석.",
  "pick 단계 선택: '첫번째/두번째/마지막/맨 위/그거/이거/네/맞아/그걸로' → select(+ordinal, '마지막'은 후보 개수).",
  "★ pick 단계에서는 사용자가 '후보를 고르는' 상황이다. 받아쓰기가 틀려 '다섯번째'가 '다 삭제'/'다섯 번 째' 처럼 들어와도 순번 의도면 select+ordinal 로 복원하라. pick 단계에선 함부로 cancel 하지 말고, '취소/그만/닫아/관둘래'처럼 명확할 때만 cancel. '삭제'라는 말만으로 cancel 하지 마라(이 화면엔 삭제 기능이 없다).",
  "기타: '여정 시작/출발하자/가자/이대로/완료/추가 완료/다 됐어'=start(정차지 추가를 마치는 것도 start), '취소/그만/관둘래/닫아'=cancel, '현재 위치/여기/지금 자리'=current, '경유지 추가/들렀다 가자/중간에 ~ 상차'=add_stop(+kind), '나중에/건너뛰어/없어요'=skip, '다시 말해줘/뭐라고'=repeat, '이전/뒤로'=back.",
  "확신이 낮으면 confidence 를 낮게(0.3~0.5) 주되 action 은 가장 가능성 높은 것으로. 반드시 emit_intent 도구로만 답하라.",
].join("\n");

/**
 * 자연어 의도 해석 (Claude Haiku 5.5). 로그인 사용자만. Anthropic 키는 함수 시크릿에만 존재.
 * 앱에서 data: { transcript, context:{ step, candidates, decided, lastPrompt } } 로 호출 → { intent }.
 */
exports.parseIntent = functions.onCall({ secrets: [ANTHROPIC_API_KEY] }, async (request) => {
  if (!request.auth) throw new functions.HttpsError("unauthenticated", "로그인이 필요해요");
  const d = request.data || {};
  const transcript = (d.transcript || "").toString().trim().slice(0, 200);
  if (!transcript) throw new functions.HttpsError("invalid-argument", "transcript 필요");
  const ctx = d.context || {};
  const payload = JSON.stringify({
    transcript,
    step: ctx.step || "dest",
    candidates: clipList(ctx.candidates),
    decided: clipList(ctx.decided),
    lastPrompt: ctx.lastPrompt ? ctx.lastPrompt.toString().slice(0, 200) : null,
  });

  try {
    // 앱은 7초 뒤 기기 내 해석으로 넘어간다. 그보다 먼저 끝나도록 시간 제한과 재시도를 줄인다.
    const client = new Anthropic({ apiKey: ANTHROPIC_API_KEY.value(), timeout: 6000, maxRetries: 1 });
    const msg = await client.messages.create({
      model: "claude-haiku-5-5",
      max_tokens: 300,
      system: INTENT_SYSTEM,
      tools: [INTENT_TOOL],
      tool_choice: { type: "tool", name: "emit_intent" },
      messages: [{ role: "user", content: payload }],
    });
    const block = (msg.content || []).find((b) => b.type === "tool_use");
    if (!block || !block.input) throw new Error("no tool_use");
    return { intent: block.input };
  } catch (e) {
    console.error("parseIntent", e && e.message);
    throw new functions.HttpsError("internal", "해석 실패");
  }
});

/** 앱이 보낸 이름 목록을 개수·길이 제한해 프롬프트에 넣는다. */
function clipList(list) {
  return Array.isArray(list) ? list.slice(0, 8).map((x) => (x == null ? "" : x.toString().slice(0, 60))) : [];
}

/** 카카오 accessToken 검증 → customToken. 앱에서 data: { accessToken } 로 호출. */
exports.authKakao = functions.onCall(async (request) => {
  const accessToken = request.data && request.data.accessToken;
  if (!accessToken) throw new functions.HttpsError("invalid-argument", "accessToken 필요");

  const res = await fetch("https://kapi.kakao.com/v2/user/me", {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!res.ok) throw new functions.HttpsError("unauthenticated", "카카오 토큰 검증 실패");
  const me = await res.json();
  const kakaoId = me.id;
  const account = me.kakao_account || {};
  const profile = {
    name: (account.profile && account.profile.nickname) || null,
    photo: (account.profile && account.profile.profile_image_url) || null,
    email: account.email || null,
  };
  const uid = await resolveUid("kakao", kakaoId, profile);
  await db.collection("users").doc(uid).set({ lastLoginAt: Date.now(), updatedAt: Date.now() }, { merge: true });
  const customToken = await admin.auth().createCustomToken(uid);
  return { customToken };
});

/** 네이버 accessToken 검증 → customToken. 앱에서 data: { accessToken } 로 호출. */
exports.authNaver = functions.onCall(async (request) => {
  const accessToken = request.data && request.data.accessToken;
  if (!accessToken) throw new functions.HttpsError("invalid-argument", "accessToken 필요");

  const res = await fetch("https://openapi.naver.com/v1/nid/me", {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!res.ok) throw new functions.HttpsError("unauthenticated", "네이버 토큰 검증 실패");
  const body = await res.json();
  if (body.resultcode !== "00") throw new functions.HttpsError("unauthenticated", "네이버 응답 오류");
  const r = body.response || {};
  const profile = { name: r.name || r.nickname || null, photo: r.profile_image || null, email: r.email || null };
  const uid = await resolveUid("naver", r.id, profile);
  await db.collection("users").doc(uid).set({ lastLoginAt: Date.now(), updatedAt: Date.now() }, { merge: true });
  const customToken = await admin.auth().createCustomToken(uid);
  return { customToken };
});
