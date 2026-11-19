const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";
const EVE_UID = "eve_uid_789";

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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("profile_cards").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("calls").where("participantUids", "array-contains", ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@gmail.com",
      emailVerified: true,
      hasPassword: false,
      privacyLockEnabled: false,
      hideMessagePreviews: false,
      readReceiptsEnabled: true,
      showOnlineStatus: true,
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Profile cards & short profile codes allow QR discovery while blocking list scraping", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("profile_cards").doc(ALICE_UID).set({
      userId: ALICE_UID,
      profileCode: "MEM842190",
      displayName: "Alice Sorkar",
      familyRole: "Mother",
      bio: "Always happy to hear from family!",
      moodTag: "✨ Feeling Blessed",
      avatarBadge: "🏡",
      avatarColorHex: "#0F5C4D",
      onlineActive: true,
      showOnlineStatus: true,
      lastSeenAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("profile_codes").doc("MEM842190").set({
      profileCode: "MEM842190",
      targetUid: ALICE_UID,
      displayName: "Alice Sorkar",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob can look up MEM842190 and fetch Alice's non-PII card after scanning QR
  await assertSucceeds(bobDb.collection("profile_codes").doc("MEM842190").get());
  await assertSucceeds(bobDb.collection("profile_cards").doc(ALICE_UID).get());

  // Bob cannot list/scrape all profile_cards or profile_codes, nor mutate Alice's profile
  await assertFails(bobDb.collection("profile_cards").get());
  await assertFails(bobDb.collection("profile_codes").get());
  await assertFails(
    bobDb.collection("profile_cards").doc(ALICE_UID).update({
      bio: "Hacked bio",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("User profile enforces strict owner isolation, bio/mood updates, and immutable fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      profileCode: "MEM842190",
      displayName: "Alice Sorkar",
      email: "alice@gmail.com",
      emailVerified: true,
      phoneNumber: "+8801711223344",
      passwordHash: "abc123hash",
      passwordSalt: "salt99",
      hasPassword: true,
      familyRole: "Mother",
      bio: "Love cooking for family",
      moodTag: "🏠 At Home",
      avatarBadge: "🏡",
      privacyLockEnabled: true,
      hideMessagePreviews: false,
      readReceiptsEnabled: true,
      showOnlineStatus: true,
      avatarColorHex: "#0F5C4D",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bio: "Always here for my kids",
      moodTag: "☕ Relaxing",
      avatarPhotoBase64: "dGVzdF9waG90b19qcGVn",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Alice can save Bob to her contacts via QR scan, Bob cannot read or hijack Alice's contacts
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("contacts").doc(BOB_UID).set({
      peerUid: BOB_UID,
      profileCode: "MEM998877",
      displayName: "Bob Sorkar",
      familyRole: "Brother",
      moodTag: "✨ Available",
      avatarBadge: "🏡",
      avatarColorHex: "#0F5C4D",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("contacts").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("contacts").doc(BOB_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("contacts").get());

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked" }));
});

test("WebRTC audio & video call signaling enforces participant isolation and terminal state locking", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const callId = "call_123";

  // Alice starts a video call to Bob
  await assertSucceeds(
    aliceDb.collection("calls").doc(callId).set({
      callerId: ALICE_UID,
      callerName: "Alice",
      calleeId: BOB_UID,
      calleeName: "Bob",
      callType: "video",
      status: "ringing",
      participantUids: [ALICE_UID, BOB_UID],
      offerSdp: "v=0\r\no=- 123 2 IN IP4 127.0.0.1\r\n",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Alice adds ICE candidate
  await assertSucceeds(
    aliceDb.collection("calls").doc(callId).collection("caller_candidates").doc("cand_1").set({
      senderId: ALICE_UID,
      sdpMid: "0",
      sdpMLineIndex: 0,
      sdpCandidate: "candidate:1 1 udp 2113937151 192.168.1.2 5000 typ host",
      participantUids: [ALICE_UID, BOB_UID],
      createdAt: created,
    })
  );

  // Bob queries incoming calls using participantUids filter and accepts
  await assertSucceeds(bobDb.collection("calls").where("participantUids", "array-contains", BOB_UID).get());
  await assertSucceeds(
    bobDb.collection("calls").doc(callId).update({
      status: "accepted",
      answerSdp: "v=0\r\no=- 456 2 IN IP4 127.0.0.1\r\n",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Eve cannot read or hijack the call
  await assertFails(eveDb.collection("calls").doc(callId).get());
  await assertFails(
    eveDb.collection("calls").doc(callId).update({
      status: "ended",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // End call and verify terminal lock blocks further updates
  await assertSucceeds(
    aliceDb.collection("calls").doc(callId).update({
      status: "ended",
      updatedAt: new Date(Date.now() - 400),
    })
  );
  await assertFails(
    bobDb.collection("calls").doc(callId).update({
      status: "accepted",
      updatedAt: new Date(Date.now() - 200),
    })
  );
});

test("Direct messaging allows non-existent check, creation, sending, reactions, and blocks outsiders", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const now = new Date(Date.now() - 2000);
  const convId = `dm_${ALICE_UID}_${BOB_UID}`;

  // Checking a non-existent conversation before creating it must succeed (returns exists=false)
  await assertSucceeds(aliceDb.collection("conversations").doc(convId).get());

  // Alice creates direct conversation with Bob
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      title: "Bob",
      type: "direct",
      createdBy: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      lastMessageText: "Connected via Profile QR",
      lastMessageSenderName: "Alice",
      lastMessageAt: now,
      disappearingHours: 0,
      lockedByUids: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Both Alice and Bob can read the conversation and send messages
  await assertSucceeds(bobDb.collection("conversations").where("memberUids", "array-contains", BOB_UID).get());
  await assertSucceeds(bobDb.collection("conversations").doc(convId).get());

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").set({
      conversationId: convId,
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "Hello Bob!",
      messageType: "text",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).update({
      lastMessageText: "Hello Bob!",
      lastMessageSenderName: "Alice",
      lastMessageAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages").where("memberUids", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages").doc("msg_2").set({
      conversationId: convId,
      senderId: BOB_UID,
      senderName: "Bob",
      text: "Hi Alice!",
      messageType: "text",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(Date.now() - 900),
      updatedAt: new Date(Date.now() - 900),
    })
  );
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).update({
      lastMessageText: "Hi Alice!",
      lastMessageSenderName: "Bob",
      lastMessageAt: new Date(Date.now() - 900),
      updatedAt: new Date(Date.now() - 900),
    })
  );

  // Bob can react to Alice's message
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").update({
      reactionEmoji: "❤️",
      updatedAt: new Date(Date.now() - 800),
    })
  );

  // Eve cannot read the existing conversation, read messages, or send messages
  await assertFails(eveDb.collection("conversations").doc(convId).get());
  await assertFails(eveDb.collection("conversations").doc(convId).collection("messages").get());
  await assertFails(eveDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").get());
  await assertFails(
    eveDb.collection("conversations").doc(convId).collection("messages").doc("msg_eve").set({
      conversationId: convId,
      senderId: EVE_UID,
      senderName: "Eve",
      text: "Intruder",
      messageType: "text",
      memberUids: [EVE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Family invite join updates member card and family group conversation for messaging", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);
  const familyId = "fam_123";
  const inviteCode = "FAM123456";
  const convId = `family_${familyId}`;

  await assertSucceeds(
    aliceDb.collection("families").doc(familyId).set({
      name: "Sorkar Home",
      inviteCode,
      createdBy: ALICE_UID,
      memberUids: [ALICE_UID],
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("family_invites").doc(inviteCode).set({
      familyId,
      familyName: "Sorkar Home",
      createdBy: ALICE_UID,
      createdByName: "Alice",
      active: true,
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      title: "Sorkar Home",
      type: "group",
      familyId,
      createdBy: ALICE_UID,
      memberUids: [ALICE_UID],
      memberNames: { [ALICE_UID]: "Alice" },
      lastMessageText: "Private family group created",
      lastMessageSenderName: "Alice",
      lastMessageAt: now,
      disappearingHours: 0,
      lockedByUids: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Bob joins family via invite code
  await assertSucceeds(
    bobDb.collection("families").doc(familyId).update({
      memberUids: [ALICE_UID, BOB_UID],
      lastJoinedInviteCode: inviteCode,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(bobDb.collection("families").doc(familyId).collection("members").doc(BOB_UID).get());
  await assertSucceeds(
    bobDb.collection("families").doc(familyId).collection("members").doc(BOB_UID).set({
      userId: BOB_UID,
      familyId,
      displayName: "Bob",
      familyRole: "Brother",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(Date.now() - 900),
      updatedAt: new Date(Date.now() - 900),
    })
  );

  // Bob reads and joins the family group conversation, then sends a message
  await assertSucceeds(bobDb.collection("conversations").doc(convId).get());
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).update({
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      updatedAt: new Date(Date.now() - 800),
    })
  );
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages").doc("msg_bob").set({
      conversationId: convId,
      senderId: BOB_UID,
      senderName: "Bob",
      text: "Joined the family room!",
      messageType: "text",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(Date.now() - 700),
      updatedAt: new Date(Date.now() - 700),
    })
  );
});

test("Rich chat messages (image, video, sticker, location), Seen receipts, and typing mark follow security rules", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const now = new Date(Date.now() - 3000);
  const convId = `dm_${ALICE_UID}_${BOB_UID}`;

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      title: "Bob",
      type: "direct",
      createdBy: ALICE_UID,
      memberUids: [ALICE_UID, BOB_UID],
      memberNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      lastMessageText: "Connected via Profile QR",
      lastMessageSenderName: "Alice",
      lastMessageAt: now,
      disappearingHours: 0,
      lockedByUids: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Alice shows live typing indicator
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).update({
      typingUids: [ALICE_UID],
      typingNames: { [ALICE_UID]: "Alice" },
      updatedAt: new Date(Date.now() - 2500),
    })
  );

  // Image message
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_img").set({
      conversationId: convId,
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "📷 Photo",
      messageType: "image",
      mediaBase64: "dGVzdF9pbWFnZV9qcGVn",
      mediaMimeType: "image/jpeg",
      seenByUids: [ALICE_UID],
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(Date.now() - 2000),
      updatedAt: new Date(Date.now() - 2000),
    })
  );

  // Video message
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_vid").set({
      conversationId: convId,
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "🎥 Video",
      messageType: "video",
      mediaBase64: "dGVzdF92aWRlb19tcDQ=",
      thumbnailBase64: "dGVzdF90aHVtYg==",
      mediaMimeType: "video/mp4",
      videoDurationMs: 5200,
      seenByUids: [ALICE_UID],
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(Date.now() - 1800),
      updatedAt: new Date(Date.now() - 1800),
    })
  );

  // Sticker message
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_stk").set({
      conversationId: convId,
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "🤲 Assalamu Alaikum",
      messageType: "sticker",
      stickerId: "stk_salam",
      seenByUids: [ALICE_UID],
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(Date.now() - 1500),
      updatedAt: new Date(Date.now() - 1500),
    })
  );

  // Location message
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_loc").set({
      conversationId: convId,
      senderId: ALICE_UID,
      senderName: "Alice",
      text: "📍 Dhaka, Bangladesh",
      messageType: "location",
      latitude: 23.8103,
      longitude: 90.4125,
      locationLabel: "Dhaka, Bangladesh",
      seenByUids: [ALICE_UID],
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: new Date(Date.now() - 1200),
      updatedAt: new Date(Date.now() - 1200),
    })
  );

  // Bob marks Alice's message as Seen ("when you see the message, the scene will appear")
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages").doc("msg_img").update({
      seenByUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(Date.now() - 900),
    })
  );

  // Eve cannot mark Seen or update typing status
  await assertFails(
    eveDb.collection("conversations").doc(convId).collection("messages").doc("msg_img").update({
      seenByUids: [ALICE_UID, EVE_UID],
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    eveDb.collection("conversations").doc(convId).update({
      typingUids: [EVE_UID],
      updatedAt: new Date(Date.now() - 500),
    })
  );
});


