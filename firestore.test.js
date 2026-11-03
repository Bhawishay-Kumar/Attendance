// @ts-nocheck
const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: cannot read user profile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Alice can read and write her own user profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      name: "Bhawishya Kumar",
      dateOfBirth: "2005-10-26",
      batch: "BATCH_A",
      courseSelection: "MAJOR_HONOURS",
      examTarget: 75,
      stipendTarget: 80,
      safetyBuffer: 4,
      futureMarkingEnabled: false,
      isDarkMode: true,
      starfieldAnimationEnabled: true,
      progressiveBlurEnabled: true,
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Bob cannot read or overwrite Alice's user profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      name: "Alice",
      batch: "BATCH_A",
      courseSelection: "MAJOR_HONOURS",
      examTarget: 75,
      stipendTarget: 80,
      safetyBuffer: 4,
      futureMarkingEnabled: false,
      isDarkMode: true,
      starfieldAnimationEnabled: true,
      progressiveBlurEnabled: true,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      name: "Hacked",
      batch: "BATCH_B",
      courseSelection: "MINOR_ELECTIVE",
      examTarget: 75,
      stipendTarget: 80,
      safetyBuffer: 4,
      futureMarkingEnabled: false,
      isDarkMode: true,
      starfieldAnimationEnabled: true,
      progressiveBlurEnabled: true,
    })
  );
});

test("Alice can write attendance records in her subcollection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("records").doc("2026-10-28").set({
      date: "2026-10-28",
      userId: ALICE_UID,
      monthId: "2026-10",
      isHoliday: false,
      periodsJson: "[]",
      attendedCredits: 6,
      conductedCredits: 7,
    })
  );
});

test("Bob cannot access Alice's attendance records", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("records").doc("2026-10-28").set({
      date: "2026-10-28",
      userId: ALICE_UID,
      monthId: "2026-10",
      isHoliday: false,
      periodsJson: "[]",
      attendedCredits: 6,
      conductedCredits: 7,
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("records").doc("2026-10-28").get());
});
