const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_recap_123";
const BOB_UID = "bob_recap_456";
const ADMIN_UID = "admin_firechat_999";
const ADMIN_EMAIL = "firechatmyanmar@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validScriptPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    sourceTitle: "Night Guard Thriller Clip",
    platform: "YOUTUBE",
    genre: "Thriller",
    viralTitles: [
      "ညစောင့်အလုပ်လုပ်ရင်း ကြုံလိုက်ရတဲ့ ထိတ်လန့်စရာ အမှန်တရား",
      "တံခါးနောက်ကွယ်က လျှို့ဝှက်ချက်ကို ဖွင့်မိတဲ့အခါ",
      "သူထင်ထားတာထက် အဆတစ်ရာ ပိုကြောက်ဖို့ကောင်းနေတဲ့ ညတစ်ည"
    ],
    openingHook: "ညသန်းခေါင်ကြီး တစ်ယောက်တည်း အလုပ်ဆင်းနေတုန်း တံခါးခေါက်သံ ကြားလိုက်ရင် ခင်ဗျား ဘာလုပ်မလဲ။",
    scenes: [
      {
        timestamp: "00:15",
        visualCue: "Guard walking down dim hallway with flashlight",
        voiceover: "ကျွန်တော်တို့ရဲ့ ဇာတ်လိုက်ကျော်ကတော့ ဒီညမှ စပြီး အလုပ်ဝင်ရတဲ့ ညစောင့်လေးပါ။"
      }
    ],
    outro: "Comment မှာ ပြောပြသွားဦးနော်။",
    estimatedDuration: "02:30",
    favorite: false,
    createdAt: now,
    updatedAt: now,
    ...overrides
  };
}

function validUserAccountPayload(uid, email = "alice@gmail.com", overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    email,
    displayName: "Alice Creator",
    coins: 0,
    trialStartDate: "2026-11-23",
    freeTrialDates: [],
    vipTier: "NONE",
    vipExpiresAtMs: 0,
    createdAt: now,
    updatedAt: now,
    ...overrides
  };
}

function validAppConfigPayload(overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    coinCostPerVideo: 10,
    kbzPayNumber: "09672804206",
    wavePayNumber: "09987654321",
    vipV1Price: 45000,
    vipV2Price: 56000,
    vipV3Price: 150000,
    announcementActive: true,
    announcementTitle: "RecapScript v2.0",
    announcementMessage: "New VIP V1/V2/V3 & Burmese AI Voice update!",
    announcementVersion: "2.0.0",
    updateUrl: "https://example.com/update",
    updatedAt: now,
    ...overrides
  };
}

function validPaymentRequestPayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    userEmail: "alice@gmail.com",
    userName: "Alice Creator",
    requestType: "VIP",
    vipTier: "VIP_V1",
    requestedCoins: 0,
    amountMmk: 45000,
    paymentMethod: "KBZPAY",
    payerPhone: "09777888999",
    transactionRef: "KBZ-123456",
    note: "VIP V1 1 month transfer",
    status: "PENDING",
    createdAt: now,
    updatedAt: now,
    ...overrides
  };
}

function validHelpMessagePayload(uid, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId: uid,
    userEmail: "alice@gmail.com",
    userName: "Alice Creator",
    subject: "VIP Activation Help",
    message: "Please check my KBZPay transfer for VIP V1.",
    status: "OPEN",
    createdAt: now,
    updatedAt: now,
    ...overrides
  };
}

function adminDb() {
  return testEnv.authenticatedContext(ADMIN_UID, {
    email: ADMIN_EMAIL,
    email_verified: true,
  }).firestore();
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("RecapScripts: owner CRUD works; cross-user and unauth rejected", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("recapScripts").get());

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const colRef = aliceDb.collection("users").doc(ALICE_UID).collection("recapScripts");
  await assertSucceeds(colRef.orderBy("createdAt", "desc").get());
  await assertSucceeds(colRef.doc("script_1").set(validScriptPayload(ALICE_UID)));
  await assertSucceeds(colRef.doc("script_1").update({ favorite: true, updatedAt: new Date(Date.now() - 500) }));
  await assertFails(colRef.doc("script_ghost").set(validScriptPayload(ALICE_UID, { isAdmin: true })));

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("recapScripts").get());
});

test("UserAccount: owner can create initial account & consume trial/coins, CANNOT grant self VIP or coins", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@gmail.com",
    email_verified: true,
  }).firestore();
  const aliceDoc = aliceDb.collection("users").doc(ALICE_UID);

  // Reject creating account with pre-granted coins or VIP
  await assertFails(aliceDoc.set(validUserAccountPayload(ALICE_UID, "alice@gmail.com", { coins: 500 })));
  await assertFails(aliceDoc.set(validUserAccountPayload(ALICE_UID, "alice@gmail.com", { vipTier: "VIP_V3" })));

  // Valid initial account creation
  await assertSucceeds(aliceDoc.set(validUserAccountPayload(ALICE_UID)));

  // Valid free trial usage (1 date added, max 3)
  await assertSucceeds(
    aliceDoc.update({
      freeTrialDates: ["2026-11-23"],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject granting self free coins or VIP
  await assertFails(
    aliceDoc.update({
      coins: 999,
      updatedAt: new Date(Date.now() - 300),
    })
  );
  await assertFails(
    aliceDoc.update({
      vipTier: "VIP_V1",
      vipExpiresAtMs: Date.now() + 86400000,
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Non-admin cannot list all users (PII isolation)
  await assertFails(aliceDb.collection("users").get());
});

test("UserAccount: Admin can list users, grant VIP V1/V2/V3 & coins, and user can spend granted coins", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).set(validUserAccountPayload(ALICE_UID));
  });

  // Email spoofing test: unverified admin email MUST fail
  const spoofAdminDb = testEnv.authenticatedContext("spoof_1", {
    email: ADMIN_EMAIL,
    email_verified: false,
  }).firestore();
  await assertFails(spoofAdminDb.collection("users").get());
  await assertFails(
    spoofAdminDb.collection("users").doc(ALICE_UID).update({
      vipTier: "VIP_V1",
      updatedAt: new Date(Date.now() - 300),
    })
  );

  // Real verified Admin succeeds
  const aDb = adminDb();
  await assertSucceeds(aDb.collection("users").get());
  await assertSucceeds(
    aDb.collection("users").doc(ALICE_UID).update({
      coins: 50,
      vipTier: "VIP_V2",
      vipExpiresAtMs: Date.now() + 60 * 86400000,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Owner can spend coins (strictly decreasing)
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      coins: 40,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("AppConfig: signed-in users can read settings; ONLY verified Admin can write", async () => {
  const aDb = adminDb();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Non-admin cannot write settings
  await assertFails(aliceDb.collection("appConfig").doc("settings").set(validAppConfigPayload()));

  // Admin creates settings
  await assertSucceeds(aDb.collection("appConfig").doc("settings").set(validAppConfigPayload()));

  // Non-admin can get settings, cannot list or update
  await assertSucceeds(aliceDb.collection("appConfig").doc("settings").get());
  await assertFails(aliceDb.collection("appConfig").get());
  await assertFails(
    aliceDb.collection("appConfig").doc("settings").update({
      coinCostPerVideo: 0,
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("PaymentRequests: user creates PENDING & queries own; Admin approves/rejects", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aDb = adminDb();

  // Reject creating pre-approved payment request
  await assertFails(
    aliceDb.collection("paymentRequests").doc("pay_1").set(
      validPaymentRequestPayload(ALICE_UID, { status: "APPROVED" })
    )
  );

  // Valid create
  await assertSucceeds(
    aliceDb.collection("paymentRequests").doc("pay_1").set(validPaymentRequestPayload(ALICE_UID))
  );

  // Alice filtered query works; unfiltered fails; Bob cannot read Alice's payment
  await assertSucceeds(
    aliceDb.collection("paymentRequests").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(aliceDb.collection("paymentRequests").get());
  await assertFails(bobDb.collection("paymentRequests").doc("pay_1").get());

  // User cannot approve own payment
  await assertFails(
    aliceDb.collection("paymentRequests").doc("pay_1").update({
      status: "APPROVED",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Admin can list all and approve
  await assertSucceeds(aDb.collection("paymentRequests").get());
  await assertSucceeds(
    aDb.collection("paymentRequests").doc("pay_1").update({
      status: "APPROVED",
      adminNote: "Verified KBZPay 45,000 MMK",
      reviewedBy: ADMIN_EMAIL,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("HelpMessages: user creates OPEN & reads own; Admin lists all & replies", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aDb = adminDb();

  await assertSucceeds(
    aliceDb.collection("helpMessages").doc("msg_1").set(validHelpMessagePayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("helpMessages").where("userId", "==", ALICE_UID).get()
  );
  await assertFails(aliceDb.collection("helpMessages").get());
  await assertFails(bobDb.collection("helpMessages").doc("msg_1").get());

  // Admin lists all & replies
  await assertSucceeds(aDb.collection("helpMessages").get());
  await assertSucceeds(
    aDb.collection("helpMessages").doc("msg_1").update({
      status: "REPLIED",
      adminReply: "Your VIP V1 is now active for 1 month!",
      repliedBy: ADMIN_EMAIL,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});
