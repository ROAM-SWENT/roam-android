// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
// Co-authored-by: Gemini 4 Neon <noreply@google.com>
// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Firestore rules smoke test. Requires Node.js 18+ and the Firebase CLI; no npm packages.
// Run from the repository root:
// firebase emulators:exec --project demo-roam-rules --only auth,firestore "node firebase/firestore/test-rules.mjs"

import { randomUUID } from "node:crypto";

const projectId = process.env.GCLOUD_PROJECT || "demo-roam-rules";
const firestoreBase =
  `http://127.0.0.1:8080/v1/projects/${projectId}/databases/(default)/documents`;
const authSignUpUrl =
  "http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signUp?key=emulator-key";

function documentName(path) {
  return `projects/${projectId}/databases/(default)/documents/${path}`;
}

function fields(values) {
  return Object.fromEntries(Object.entries(values).map(([key, value]) => [key, toFirestoreValue(value)]));
}

function toFirestoreValue(value) {
  if (value === null) return { nullValue: null };
  if (typeof value === "string") return { stringValue: value };
  if (typeof value === "boolean") return { booleanValue: value };
  if (typeof value === "number") {
    if (Number.isInteger(value)) return { integerValue: value.toString() };
    return { doubleValue: value };
  }
  if (value instanceof Uint8Array || Buffer.isBuffer(value)) {
    return { bytesValue: Buffer.from(value).toString("base64") };
  }
  if (Array.isArray(value)) return { arrayValue: { values: value.map(toFirestoreValue) } };
  if (typeof value === "object") {
    return { mapValue: { fields: fields(value) } };
  }
  throw new Error(`Unsupported Firestore test value: ${value}`);
}

function profileWrite(uid, username, extraFields = {}) {
  return {
    update: {
      name: documentName(`publicProfiles/${uid}`),
      fields: fields({ uid, username, usernameLower: username.toLowerCase(), ...extraFields }),
    },
  };
}

function claimWrite(uid, username) {
  return {
    update: {
      name: documentName(`usernameClaims/${username.toLowerCase()}`),
      fields: fields({ uid }),
    },
  };
}

function friendshipWrite(requesterUid, recipientUid, status = "PENDING") {
  const participantUids = [requesterUid, recipientUid].sort();
  return {
    update: {
      name: documentName(`friendships/${participantUids[0]}~${participantUids[1]}`),
      fields: fields({ participantUids, requesterUid, recipientUid, status }),
    },
  };
}

function notificationInstallationWrite(uid, fid) {
  return {
    update: {
      name: documentName(`notificationInstallations/${uid}/installations/${fid}`),
      fields: fields({ fid }),
    },
    updateTransforms: [{ fieldPath: "updatedAt", setToServerValue: "REQUEST_TIME" }],
  };
}

function savedGuideWrite(uid, guideId, data = { monumentName: "Test guide" }) {
  return {
    update: {
      name: documentName(`users/${uid}/savedGuides/${guideId}`),
      fields: fields(data),
    },
  };
}

async function createUser() {
  const response = await fetch(authSignUpUrl, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      email: `${randomUUID()}@example.test`,
      password: "rules-test-password",
      returnSecureToken: true,
    }),
  });
  const body = await response.json();
  if (!response.ok) throw new Error(`Auth emulator sign-up failed: ${JSON.stringify(body)}`);
  return { uid: body.localId, token: body.idToken };
}

async function firestoreRequest(path, token, options = {}) {
  const url = path.startsWith(":") ? `${firestoreBase}${path}` : `${firestoreBase}/${path}`;
  return fetch(url, {
    ...options,
    headers: {
      Authorization: `Bearer ${token}`,
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers,
    },
  });
}

async function commit(token, writes) {
  return firestoreRequest(":commit", token, {
    method: "POST",
    body: JSON.stringify({ writes }),
  });
}

async function queryFriendships(token, uid) {
  return firestoreRequest(":runQuery", token, {
    method: "POST",
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: "friendships" }],
        where: {
          fieldFilter: {
            field: { fieldPath: "participantUids" },
            op: "ARRAY_CONTAINS",
            value: { stringValue: uid },
          },
        },
      },
    }),
  });
}

async function querySavedGuides(token, uid) {
  return fetch(`${firestoreBase}/users/${uid}:runQuery`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: "savedGuides" }],
      },
    }),
  });
}

async function expectAllowed(label, response) {
  const bodyText = await response.text();
  if (!response.ok) throw new Error(`${label} failed (${response.status}): ${bodyText}`);
  console.log(`PASS ${label}`);
  try {
    return JSON.parse(bodyText);
  } catch {
    return bodyText;
  }
}

async function expectDenied(label, response) {
  const body = await response.json();
  if (response.status !== 403 || body.error?.status !== "PERMISSION_DENIED") {
    throw new Error(
      `${label} expected PERMISSION_DENIED, got ${response.status}: ${JSON.stringify(body)}`,
    );
  }
  console.log(`PASS ${label}`);
}

async function expectNotFound(label, response) {
  const body = await response.json();
  if (response.status !== 404 || body.error?.status !== "NOT_FOUND") {
    throw new Error(`${label} expected NOT_FOUND, got ${response.status}: ${JSON.stringify(body)}`);
  }
  console.log(`PASS ${label}`);
}

async function expectFriendshipQuery(label, token, uid, otherUid, status) {
  const response = await queryFriendships(token, uid);
  const body = await response.json();
  if (!response.ok) throw new Error(`${label} failed (${response.status}): ${JSON.stringify(body)}`);
  const expectedName = documentName(
    `friendships/${[uid, otherUid].sort()[0]}~${[uid, otherUid].sort()[1]}`,
  );
  const found = body.some(
    (result) =>
      result.document?.name === expectedName &&
      result.document.fields.status.stringValue === status,
  );
  if (!found) throw new Error(`${label} did not return ${expectedName} with status ${status}.`);
  console.log(`PASS ${label}`);
}

const owner = await createUser();
const otherUser = await createUser();
const stranger = await createUser();

await expectAllowed(
  "authenticated registration",
  await commit(owner.token, [profileWrite(owner.uid, "Roamer"), claimWrite(owner.uid, "Roamer")]),
);

await expectAllowed(
  "user can query their empty saved guides",
  await querySavedGuides(owner.token, owner.uid),
);
const savedGuidePath = `users/${owner.uid}/savedGuides/test-guide`;
await expectAllowed(
  "user can create a saved guide",
  await commit(owner.token, [savedGuideWrite(owner.uid, "test-guide")]),
);
await expectAllowed(
  "user can delete their saved guide",
  await firestoreRequest(savedGuidePath, owner.token, { method: "DELETE" }),
);
await expectDenied(
  "user cannot create another user's saved guide",
  await commit(otherUser.token, [savedGuideWrite(owner.uid, "other-guide")]),
);

await expectAllowed(
  "user can register their notification installation",
  await commit(
    owner.token,
    [notificationInstallationWrite(owner.uid, "owner-device-fid")],
  ),
);
await expectDenied(
  "user cannot register another user's notification installation",
  await commit(
    otherUser.token,
    [notificationInstallationWrite(owner.uid, "other-device-fid")],
  ),
);
await expectDenied(
  "notification installations cannot be read by clients",
  await firestoreRequest(
    `notificationInstallations/${owner.uid}/installations/owner-device-fid`,
    owner.token,
  ),
);

await expectDenied(
  "duplicate username rejected",
  await commit(otherUser.token, [
    profileWrite(otherUser.uid, "ROAMER"),
    claimWrite(otherUser.uid, "ROAMER"),
  ]),
);

await expectDenied(
  "another user cannot modify the profile",
  await commit(otherUser.token, [profileWrite(owner.uid, "Hijacked")]),
);

await expectDenied(
  "claim cannot be deleted while its profile uses it",
  await firestoreRequest("usernameClaims/roamer", owner.token, { method: "DELETE" }),
);

await expectAllowed(
  "username rename releases old claim atomically",
  await commit(owner.token, [
    profileWrite(owner.uid, "RoamerNew"),
    claimWrite(owner.uid, "RoamerNew"),
    { delete: documentName("usernameClaims/roamer") },
  ]),
);

await expectAllowed(
  "released old username can be claimed by another user",
  await commit(otherUser.token, [
    profileWrite(otherUser.uid, "Roamer"),
    claimWrite(otherUser.uid, "Roamer"),
  ]),
);

await expectDenied(
  "request to an unregistered profile rejected",
  await commit(owner.token, [friendshipWrite(owner.uid, stranger.uid)]),
);

await expectDenied(
  "accepted friendship cannot be created directly",
  await commit(owner.token, [friendshipWrite(owner.uid, otherUser.uid, "ACCEPTED")]),
);

await expectAllowed(
  "pending friend request created",
  await commit(owner.token, [friendshipWrite(owner.uid, otherUser.uid)]),
);

await expectDenied(
  "duplicate pending friend request rejected",
  await commit(owner.token, [friendshipWrite(owner.uid, otherUser.uid)]),
);

await expectDenied(
  "reciprocal pending request rejected without implicit acceptance",
  await commit(otherUser.token, [friendshipWrite(otherUser.uid, owner.uid)]),
);

await expectFriendshipQuery(
  "request participant can query outgoing request",
  owner.token,
  owner.uid,
  otherUser.uid,
  "PENDING",
);
await expectFriendshipQuery(
  "request participant can query incoming request",
  otherUser.token,
  otherUser.uid,
  owner.uid,
  "PENDING",
);
const strangerQueryResponse = await queryFriendships(stranger.token, stranger.uid);
const strangerQueryBody = await strangerQueryResponse.json();
if (
  !strangerQueryResponse.ok ||
  strangerQueryBody.some((result) => result.document !== undefined)
) {
  throw new Error(
    `Nonparticipant query should return no friendship documents: ${JSON.stringify(strangerQueryBody)}`,
  );
}
console.log("PASS nonparticipant query returns no friendship documents");

const friendshipPath =
  `friendships/${[owner.uid, otherUser.uid].sort()[0]}~${[owner.uid, otherUser.uid].sort()[1]}`;
await expectNotFound(
  "participant can check an absent friendship pair",
  await firestoreRequest(
    `friendships/${[owner.uid, stranger.uid].sort()[0]}~${[owner.uid, stranger.uid].sort()[1]}`,
    owner.token,
  ),
);
await expectDenied(
  "nonparticipant cannot check an absent friendship pair",
  await firestoreRequest(
    `friendships/${[otherUser.uid, stranger.uid].sort()[0]}~${[otherUser.uid, stranger.uid].sort()[1]}`,
    owner.token,
  ),
);
await expectDenied(
  "nonparticipant cannot read a known friendship document",
  await firestoreRequest(friendshipPath, stranger.token),
);

await expectDenied(
  "requester cannot accept their own request",
  await commit(owner.token, [friendshipWrite(owner.uid, otherUser.uid, "ACCEPTED")]),
);

await expectAllowed(
  "recipient can accept a pending request",
  await commit(otherUser.token, [friendshipWrite(owner.uid, otherUser.uid, "ACCEPTED")]),
);

await expectFriendshipQuery(
  "friend list query can read an accepted friendship",
  owner.token,
  owner.uid,
  otherUser.uid,
  "ACCEPTED",
);

await expectAllowed(
  "participant can remove an accepted friendship",
  await firestoreRequest(friendshipPath, owner.token, { method: "DELETE" }),
);

await expectAllowed(
  "pending request created for rejection",
  await commit(owner.token, [friendshipWrite(owner.uid, otherUser.uid)]),
);
await expectAllowed(
  "recipient can reject a pending request",
  await firestoreRequest(friendshipPath, otherUser.token, { method: "DELETE" }),
);
await expectAllowed(
  "original requester can send a new request after rejection",
  await commit(owner.token, [friendshipWrite(owner.uid, otherUser.uid)]),
);
await expectAllowed(
  "recipient can reject the resent request",
  await firestoreRequest(friendshipPath, otherUser.token, { method: "DELETE" }),
);
await expectAllowed(
  "former recipient can send a new request after rejection",
  await commit(otherUser.token, [friendshipWrite(otherUser.uid, owner.uid)]),
);
await expectAllowed(
  "requester can cancel a pending request",
  await firestoreRequest(friendshipPath, otherUser.token, { method: "DELETE" }),
);

// --- users/{ownerUid}/savedGuides/{guideId} rules tests ---

const guideOwner = await createUser();
const friend = await createUser();
const unlistedFriend = await createUser();
const follower = await createUser();
const strangerUser = await createUser();

// Set up public profiles
await commit(guideOwner.token, [
  profileWrite(guideOwner.uid, `Owner_${guideOwner.uid.slice(0, 8)}`, { isPublic: true }),
  claimWrite(guideOwner.uid, `Owner_${guideOwner.uid.slice(0, 8)}`),
]);
await commit(friend.token, [
  profileWrite(friend.uid, `Friend_${friend.uid.slice(0, 8)}`),
  claimWrite(friend.uid, `Friend_${friend.uid.slice(0, 8)}`),
]);
await commit(unlistedFriend.token, [
  profileWrite(unlistedFriend.uid, `Unlist_${unlistedFriend.uid.slice(0, 8)}`),
  claimWrite(unlistedFriend.uid, `Unlist_${unlistedFriend.uid.slice(0, 8)}`),
]);
await commit(follower.token, [
  profileWrite(follower.uid, `Follow_${follower.uid.slice(0, 8)}`),
  claimWrite(follower.uid, `Follow_${follower.uid.slice(0, 8)}`),
]);
await commit(strangerUser.token, [
  profileWrite(strangerUser.uid, `Strang_${strangerUser.uid.slice(0, 8)}`),
  claimWrite(strangerUser.uid, `Strang_${strangerUser.uid.slice(0, 8)}`),
]);

// Set up accepted friendships via admin
await commit("owner", [
  friendshipWrite(guideOwner.uid, friend.uid, "ACCEPTED"),
  friendshipWrite(guideOwner.uid, unlistedFriend.uid, "ACCEPTED"),
]);

// Set up follow relation (guideOwner is followed by follower) via admin
await commit("owner", [
  {
    update: {
      name: documentName(`follows/${guideOwner.uid}~${follower.uid}`),
      fields: fields({ ownerUid: guideOwner.uid, followerUid: follower.uid }),
    },
  },
]);

// 1. Owner can create, read, update, and delete their own guide.
const ownerGuidePath = `users/${guideOwner.uid}/savedGuides/guide-owner-crud`;
await expectAllowed(
  "owner can create own guide",
  await commit(guideOwner.token, [
    savedGuideWrite(guideOwner.uid, "guide-owner-crud", {
      monumentName: "Eiffel Tower",
      photo: "bytes123",
      confidence: 0.95,
      shareAudience: "ALL_FRIENDS",
    }),
  ]),
);
await expectAllowed(
  "owner can read own guide",
  await firestoreRequest(ownerGuidePath, guideOwner.token),
);
await expectAllowed(
  "owner can update own guide",
  await commit(guideOwner.token, [
    savedGuideWrite(guideOwner.uid, "guide-owner-crud", {
      monumentName: "Eiffel Tower Updated",
      photo: "bytes123",
      confidence: 0.99,
      shareAudience: "ALL_FRIENDS",
    }),
  ]),
);
await expectAllowed(
  "owner can delete own guide",
  await firestoreRequest(ownerGuidePath, guideOwner.token, { method: "DELETE" }),
);

// 2. NONE: denied to accepted bidirectional friend and stranger.
const noneGuidePath = `users/${guideOwner.uid}/savedGuides/guide-none`;
await expectAllowed(
  "owner can create NONE audience guide",
  await commit(guideOwner.token, [
    savedGuideWrite(guideOwner.uid, "guide-none", {
      monumentName: "Private Monument",
      photo: "bytes",
      shareAudience: "NONE",
    }),
  ]),
);
await expectDenied(
  "NONE audience denied to accepted friend",
  await firestoreRequest(noneGuidePath, friend.token),
);
await expectDenied(
  "NONE audience denied to stranger",
  await firestoreRequest(noneGuidePath, strangerUser.token),
);

// 3. SELECTED_FRIENDS: allowed to listed accepted friend; denied to unlisted accepted friend and non-friend.
const selectedGuidePath = `users/${guideOwner.uid}/savedGuides/guide-selected`;
await expectAllowed(
  "owner can create SELECTED_FRIENDS guide",
  await commit(guideOwner.token, [
    savedGuideWrite(guideOwner.uid, "guide-selected", {
      monumentName: "Selected Friends Monument",
      photo: "bytes",
      shareAudience: "SELECTED_FRIENDS",
      sharedWithUids: [friend.uid],
    }),
  ]),
);
await expectAllowed(
  "SELECTED_FRIENDS allowed to listed accepted friend",
  await firestoreRequest(selectedGuidePath, friend.token),
);
await expectDenied(
  "SELECTED_FRIENDS denied to unlisted accepted friend",
  await firestoreRequest(selectedGuidePath, unlistedFriend.token),
);
await expectDenied(
  "SELECTED_FRIENDS denied to non-friend",
  await firestoreRequest(selectedGuidePath, strangerUser.token),
);

// If stranger is somehow in sharedWithUids, read is still denied because not an accepted friend
const selectedWithStrangerPath = `users/${guideOwner.uid}/savedGuides/guide-selected-stranger`;
await commit(guideOwner.token, [
  savedGuideWrite(guideOwner.uid, "guide-selected-stranger", {
    monumentName: "Selected With Stranger Monument",
    photo: "bytes",
    shareAudience: "SELECTED_FRIENDS",
    sharedWithUids: [friend.uid, strangerUser.uid],
  }),
]);
await expectDenied(
  "SELECTED_FRIENDS denied to listed non-friend",
  await firestoreRequest(selectedWithStrangerPath, strangerUser.token),
);

// 4. ALL_FRIENDS: allowed to accepted friend; denied to stranger / non-friend.
const allFriendsGuidePath = `users/${guideOwner.uid}/savedGuides/guide-all-friends`;
await expectAllowed(
  "owner can create ALL_FRIENDS guide",
  await commit(guideOwner.token, [
    savedGuideWrite(guideOwner.uid, "guide-all-friends", {
      monumentName: "All Friends Monument",
      photo: "bytes",
      shareAudience: "ALL_FRIENDS",
    }),
  ]),
);
await expectAllowed(
  "ALL_FRIENDS allowed to accepted friend",
  await firestoreRequest(allFriendsGuidePath, friend.token),
);
await expectAllowed(
  "ALL_FRIENDS allowed to other accepted friend",
  await firestoreRequest(allFriendsGuidePath, unlistedFriend.token),
);
await expectDenied(
  "ALL_FRIENDS denied to stranger",
  await firestoreRequest(allFriendsGuidePath, strangerUser.token),
);

// 5. Legacy/#198 doc (no shareAudience field): allowed to accepted friend; denied to stranger.
const legacyGuidePath = `users/${guideOwner.uid}/savedGuides/guide-legacy`;
await expectAllowed(
  "owner can create legacy guide without shareAudience",
  await commit(guideOwner.token, [
    savedGuideWrite(guideOwner.uid, "guide-legacy", {
      monumentName: "Legacy Monument",
      photo: "bytes",
    }),
  ]),
);
await expectAllowed(
  "legacy guide allowed to accepted friend",
  await firestoreRequest(legacyGuidePath, friend.token),
);
await expectDenied(
  "legacy guide denied to stranger",
  await firestoreRequest(legacyGuidePath, strangerUser.token),
);

// 6. ALL_FOLLOWERS: allowed to accepted friend and to unidirectional follower; denied to stranger.
const allFollowersGuidePath = `users/${guideOwner.uid}/savedGuides/guide-all-followers`;
await expectAllowed(
  "owner can create ALL_FOLLOWERS guide",
  await commit(guideOwner.token, [
    savedGuideWrite(guideOwner.uid, "guide-all-followers", {
      monumentName: "All Followers Monument",
      photo: "bytes",
      shareAudience: "ALL_FOLLOWERS",
    }),
  ]),
);
await expectAllowed(
  "ALL_FOLLOWERS allowed to accepted friend",
  await firestoreRequest(allFollowersGuidePath, friend.token),
);
await expectAllowed(
  "ALL_FOLLOWERS allowed to unidirectional follower",
  await firestoreRequest(allFollowersGuidePath, follower.token),
);
await expectDenied(
  "ALL_FOLLOWERS denied to stranger",
  await firestoreRequest(allFollowersGuidePath, strangerUser.token),
);

// 7. Recipient dismissal:
// Authorized friend can update *only* dismissedByUids (adding own UID) and sharedWithUids (removing own UID),
// after which their read is denied. Recipient cannot delete the owner's guide, cannot modify monumentName/photo,
// and cannot add another user's UID to dismissedByUids.
const dismissGuidePath = `users/${guideOwner.uid}/savedGuides/guide-dismiss`;
await commit(guideOwner.token, [
  savedGuideWrite(guideOwner.uid, "guide-dismiss", {
    monumentName: "Dismissible Monument",
    photo: "bytes",
    shareAudience: "SELECTED_FRIENDS",
    sharedWithUids: [friend.uid, unlistedFriend.uid],
    dismissedByUids: [],
  }),
]);

// Recipient cannot delete the owner's guide
await expectDenied(
  "recipient cannot delete owner guide",
  await firestoreRequest(dismissGuidePath, friend.token, { method: "DELETE" }),
);

// Recipient cannot modify monumentName or other non-dismissal fields
await expectDenied(
  "recipient cannot modify monumentName",
  await commit(friend.token, [
    {
      update: {
        name: documentName(dismissGuidePath),
        fields: fields({
          monumentName: "Hacked Monument Name",
          dismissedByUids: [friend.uid],
          sharedWithUids: [unlistedFriend.uid],
        }),
      },
      updateMask: {
        fieldPaths: ["monumentName", "dismissedByUids", "sharedWithUids"],
      },
    },
  ]),
);

// Recipient cannot add another user's UID to dismissedByUids
await expectDenied(
  "recipient cannot add another user to dismissedByUids",
  await commit(friend.token, [
    {
      update: {
        name: documentName(dismissGuidePath),
        fields: fields({
          dismissedByUids: [unlistedFriend.uid],
          sharedWithUids: [unlistedFriend.uid],
        }),
      },
      updateMask: {
        fieldPaths: ["dismissedByUids", "sharedWithUids"],
      },
    },
  ]),
);

// Recipient dismissal: authorized friend updates only dismissedByUids (adding own UID) and sharedWithUids (removing own UID)
await expectAllowed(
  "recipient can dismiss shared guide",
  await commit(friend.token, [
    {
      update: {
        name: documentName(dismissGuidePath),
        fields: fields({
          dismissedByUids: [friend.uid],
          sharedWithUids: [unlistedFriend.uid],
        }),
      },
      updateMask: {
        fieldPaths: ["dismissedByUids", "sharedWithUids"],
      },
    },
  ]),
);

// After dismissal, their read is denied
await expectDenied(
  "read denied to dismissed recipient",
  await firestoreRequest(dismissGuidePath, friend.token),
);

// But unlistedFriend (who wasn't dismissed) can still read
await expectAllowed(
  "non-dismissed friend can still read",
  await firestoreRequest(dismissGuidePath, unlistedFriend.token),
);

// Dismissed recipient on ALL_FRIENDS guide is denied read by dismissedByUids guard
const allFriendsDismissedPath = `users/${guideOwner.uid}/savedGuides/guide-all-friends-dismissed`;
await commit(guideOwner.token, [
  savedGuideWrite(guideOwner.uid, "guide-all-friends-dismissed", {
    monumentName: "Dismissed All Friends Monument",
    photo: "bytes",
    shareAudience: "ALL_FRIENDS",
    dismissedByUids: [friend.uid],
  }),
]);
await expectDenied(
  "dismissed friend on ALL_FRIENDS guide is denied read",
  await firestoreRequest(allFriendsDismissedPath, friend.token),
);

// --- users/{ownerUid}/savedGuides collection list (runQuery) rules tests ---

async function runSavedGuidesQuery(token, ownerUid, where = null) {
  const structuredQuery = {
    from: [{ collectionId: "savedGuides" }],
    ...(where ? { where } : {}),
  };
  return firestoreRequest(`users/${ownerUid}:runQuery`, token, {
    method: "POST",
    body: JSON.stringify({ structuredQuery }),
  });
}

// 1. Unfiltered list by accepted friend is denied (PERMISSION_DENIED) — proving "rules are not filters".
await expectDenied(
  "unfiltered savedGuides query by accepted friend is denied",
  await runSavedGuidesQuery(friend.token, guideOwner.uid),
);

// 2. whereIn (shareAudience in ['ALL_FRIENDS', 'ALL_FOLLOWERS']) by accepted friend is allowed.
const broadcastFilter = {
  fieldFilter: {
    field: { fieldPath: "shareAudience" },
    op: "IN",
    value: {
      arrayValue: {
        values: [{ stringValue: "ALL_FRIENDS" }, { stringValue: "ALL_FOLLOWERS" }],
      },
    },
  },
};

const broadcastResponse = await runSavedGuidesQuery(friend.token, guideOwner.uid, broadcastFilter);
const broadcastBody = await expectAllowed(
  "whereIn ALL_FRIENDS/ALL_FOLLOWERS by accepted friend is allowed",
  broadcastResponse,
);
const broadcastIds = broadcastBody
  .filter((r) => r.document)
  .map((r) => r.document.name.split("/").pop());
if (
  !broadcastIds.includes("guide-all-friends") ||
  !broadcastIds.includes("guide-all-followers") ||
  broadcastIds.includes("guide-none") ||
  broadcastIds.includes("guide-selected")
) {
  throw new Error(`whereIn returned unexpected docs: ${JSON.stringify(broadcastIds)}`);
}

// 3. whereEqualTo("shareAudience", "SELECTED_FRIENDS") + whereArrayContains("sharedWithUids", friendUid) is allowed.
const selectedFilter = {
  compositeFilter: {
    op: "AND",
    filters: [
      {
        fieldFilter: {
          field: { fieldPath: "shareAudience" },
          op: "EQUAL",
          value: { stringValue: "SELECTED_FRIENDS" },
        },
      },
      {
        fieldFilter: {
          field: { fieldPath: "sharedWithUids" },
          op: "ARRAY_CONTAINS",
          value: { stringValue: friend.uid },
        },
      },
    ],
  },
};

const selectedResponse = await runSavedGuidesQuery(friend.token, guideOwner.uid, selectedFilter);
const selectedBody = await expectAllowed(
  "whereEqualTo SELECTED_FRIENDS and arrayContains friendUid by listed friend is allowed",
  selectedResponse,
);
const selectedIds = selectedBody
  .filter((r) => r.document)
  .map((r) => r.document.name.split("/").pop());
if (!selectedIds.includes("guide-selected") || selectedIds.includes("guide-none")) {
  throw new Error(`selectedQuery returned unexpected docs: ${JSON.stringify(selectedIds)}`);
}

// 4. whereIn (['ALL_FRIENDS', 'ALL_FOLLOWERS']) by stranger is denied.
await expectDenied(
  "whereIn ALL_FRIENDS/ALL_FOLLOWERS by stranger is denied",
  await runSavedGuidesQuery(strangerUser.token, guideOwner.uid, broadcastFilter),
);

// 5. whereEqualTo("shareAudience", "ALL_FOLLOWERS") by unidirectional follower is allowed.
const followersFilter = {
  fieldFilter: {
    field: { fieldPath: "shareAudience" },
    op: "EQUAL",
    value: { stringValue: "ALL_FOLLOWERS" },
  },
};

const followersResponse = await runSavedGuidesQuery(follower.token, guideOwner.uid, followersFilter);
const followersBody = await expectAllowed(
  "whereEqualTo ALL_FOLLOWERS by unidirectional follower is allowed",
  followersResponse,
);
const followersIds = followersBody
  .filter((r) => r.document)
  .map((r) => r.document.name.split("/").pop());
if (!followersIds.includes("guide-all-followers") || followersIds.includes("guide-all-friends")) {
  throw new Error(`followersQuery returned unexpected docs: ${JSON.stringify(followersIds)}`);
}

// 6. whereEqualTo("shareAudience", "ALL_FRIENDS") by unidirectional follower is denied.
const friendsFilter = {
  fieldFilter: {
    field: { fieldPath: "shareAudience" },
    op: "EQUAL",
    value: { stringValue: "ALL_FRIENDS" },
  },
};

await expectDenied(
  "whereEqualTo ALL_FRIENDS by unidirectional follower is denied",
  await runSavedGuidesQuery(follower.token, guideOwner.uid, friendsFilter),
);

// --- PR #279: Account privacy, friendships gating, follows CRUD, instant revocation, blockedUsers ---

function followWrite(ownerUid, followerUid, docId = `${ownerUid}~${followerUid}`, data = { ownerUid, followerUid }) {
  return {
    update: {
      name: documentName(`follows/${docId}`),
      fields: fields(data),
    },
  };
}

function blockedUserWrite(blockerUid, blockedUid, data = { blockedUid }) {
  return {
    update: {
      name: documentName(`users/${blockerUid}/blockedUsers/${blockedUid}`),
      fields: fields(data),
    },
  };
}

async function queryFollows(token, fieldPath, uid) {
  return firestoreRequest(":runQuery", token, {
    method: "POST",
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: "follows" }],
        where: {
          fieldFilter: {
            field: { fieldPath },
            op: "EQUAL",
            value: { stringValue: uid },
          },
        },
      },
    }),
  });
}

async function queryBlockedUsers(token, blockerUid) {
  return firestoreRequest(`users/${blockerUid}:runQuery`, token, {
    method: "POST",
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: "blockedUsers" }],
      },
    }),
  });
}

const privOwner = await createUser();
const privOther = await createUser();
const privLegacy = await createUser();
const privFollower = await createUser();
const privFriend = await createUser();
const privStranger = await createUser();

const privOwnerName = `POwn_${privOwner.uid.slice(0, 8)}`;
const privOtherName = `POth_${privOther.uid.slice(0, 8)}`;
const privLegacyName = `PLeg_${privLegacy.uid.slice(0, 8)}`;
const privFollowerName = `PFol_${privFollower.uid.slice(0, 8)}`;
const privFriendName = `PFrd_${privFriend.uid.slice(0, 8)}`;

await expectAllowed(
  "register privOwner profile",
  await commit(privOwner.token, [
    profileWrite(privOwner.uid, privOwnerName, { isPublic: false, allowFriendRequests: true }),
    claimWrite(privOwner.uid, privOwnerName),
  ]),
);
await expectAllowed(
  "register privOther profile",
  await commit(privOther.token, [
    profileWrite(privOther.uid, privOtherName, { isPublic: false, allowFriendRequests: true }),
    claimWrite(privOther.uid, privOtherName),
  ]),
);
await expectAllowed(
  "register legacy profile without privacy fields",
  await commit(privLegacy.token, [
    profileWrite(privLegacy.uid, privLegacyName),
    claimWrite(privLegacy.uid, privLegacyName),
  ]),
);
await expectAllowed(
  "register privFollower profile",
  await commit(privFollower.token, [
    profileWrite(privFollower.uid, privFollowerName, { isPublic: false, allowFriendRequests: true }),
    claimWrite(privFollower.uid, privFollowerName),
  ]),
);
await expectAllowed(
  "register privFriend profile",
  await commit(privFriend.token, [
    profileWrite(privFriend.uid, privFriendName, { isPublic: false, allowFriendRequests: true }),
    claimWrite(privFriend.uid, privFriendName),
  ]),
);

// 1. publicProfiles privacy fields
await expectAllowed(
  "owner can set isPublic=true and allowFriendRequests=false",
  await commit(privOwner.token, [
    profileWrite(privOwner.uid, privOwnerName, { isPublic: true, allowFriendRequests: false }),
  ]),
);
await expectAllowed(
  "owner can set private account with allowFriendRequests=false",
  await commit(privOwner.token, [
    profileWrite(privOwner.uid, privOwnerName, { isPublic: false, allowFriendRequests: false }),
  ]),
);
await expectDenied(
  "non-boolean isPublic denied",
  await commit(privOwner.token, [
    profileWrite(privOwner.uid, privOwnerName, { isPublic: "true", allowFriendRequests: true }),
  ]),
);
await expectDenied(
  "non-boolean allowFriendRequests denied",
  await commit(privOwner.token, [
    profileWrite(privOwner.uid, privOwnerName, { isPublic: false, allowFriendRequests: 1 }),
  ]),
);
await expectDenied(
  "other user cannot modify owner privacy fields",
  await commit(privOther.token, [
    profileWrite(privOwner.uid, privOwnerName, { isPublic: true, allowFriendRequests: true }),
  ]),
);

// 2. friendships gating & blacklisting
// privOwner currently has allowFriendRequests: false
await expectDenied(
  "friend request denied when recipient has allowFriendRequests=false",
  await commit(privOther.token, [friendshipWrite(privOther.uid, privOwner.uid)]),
);

// Sender with allowFriendRequests=false CAN still send a request to recipient with allowFriendRequests=true, and recipient can accept it.
await expectAllowed(
  "sender with allowFriendRequests=false can send friend request to receptive recipient",
  await commit(privOwner.token, [friendshipWrite(privOwner.uid, privOther.uid)]),
);
await expectAllowed(
  "recipient can accept friend request from sender with allowFriendRequests=false",
  await commit(privOther.token, [friendshipWrite(privOwner.uid, privOther.uid, "ACCEPTED")]),
);
// Clean up friendship between privOwner and privOther
const ownerOtherPair = `friendships/${[privOwner.uid, privOther.uid].sort().join("~")}`;
await expectAllowed(
  "remove privOwner-privOther friendship for blacklist tests",
  await firestoreRequest(ownerOtherPair, privOwner.token, { method: "DELETE" }),
);

// Re-enable allowFriendRequests on privOwner and blacklist privOther
await expectAllowed(
  "privOwner enables allowFriendRequests",
  await commit(privOwner.token, [
    profileWrite(privOwner.uid, privOwnerName, { isPublic: true, allowFriendRequests: true }),
  ]),
);
await expectAllowed(
  "privOwner blacklists privOther",
  await commit(privOwner.token, [blockedUserWrite(privOwner.uid, privOther.uid)]),
);
await expectDenied(
  "friend request denied when recipient blacklisted sender",
  await commit(privOther.token, [friendshipWrite(privOther.uid, privOwner.uid)]),
);
await expectAllowed(
  "privOwner unblocks privOther",
  await firestoreRequest(`users/${privOwner.uid}/blockedUsers/${privOther.uid}`, privOwner.token, {
    method: "DELETE",
  }),
);
await expectAllowed(
  "unblocked sender can create friend request again",
  await commit(privOther.token, [friendshipWrite(privOther.uid, privOwner.uid)]),
);
await expectAllowed(
  "clean up unblocked friend request",
  await firestoreRequest(ownerOtherPair, privOther.token, { method: "DELETE" }),
);

// 3. follows CRUD & validation
// privOwner is public (isPublic: true); privOther is private (isPublic: false); privLegacy has no isPublic
await expectDenied(
  "follow denied when target is private (isPublic=false)",
  await commit(privFollower.token, [followWrite(privOther.uid, privFollower.uid)]),
);
await expectDenied(
  "follow denied when target is missing isPublic (legacy)",
  await commit(privFollower.token, [followWrite(privLegacy.uid, privFollower.uid)]),
);
await expectDenied(
  "self-follow denied",
  await commit(privOwner.token, [followWrite(privOwner.uid, privOwner.uid)]),
);
await expectDenied(
  "follow with wrong followId denied",
  await commit(privFollower.token, [
    followWrite(privOwner.uid, privFollower.uid, `${privFollower.uid}~${privOwner.uid}`),
  ]),
);
await expectDenied(
  "follow with extra fields denied",
  await commit(privFollower.token, [
    followWrite(privOwner.uid, privFollower.uid, `${privOwner.uid}~${privFollower.uid}`, {
      ownerUid: privOwner.uid,
      followerUid: privFollower.uid,
      extra: true,
    }),
  ]),
);
await expectDenied(
  "follow spoofing another followerUid denied",
  await commit(privStranger.token, [followWrite(privOwner.uid, privFollower.uid)]),
);
await expectAllowed(
  "privOwner blacklists privStranger before follow attempt",
  await commit(privOwner.token, [blockedUserWrite(privOwner.uid, privStranger.uid)]),
);
await expectDenied(
  "follow denied when target blacklisted follower",
  await commit(privStranger.token, [followWrite(privOwner.uid, privStranger.uid)]),
);
await expectAllowed(
  "clean up privStranger block",
  await firestoreRequest(`users/${privOwner.uid}/blockedUsers/${privStranger.uid}`, privOwner.token, {
    method: "DELETE",
  }),
);

// Valid follow create
const followDocPath = `follows/${privOwner.uid}~${privFollower.uid}`;
await expectAllowed(
  "follow allowed when target isPublic=true and follower not blacklisted",
  await commit(privFollower.token, [followWrite(privOwner.uid, privFollower.uid)]),
);
await expectDenied(
  "follow update denied to follower",
  await commit(privFollower.token, [followWrite(privOwner.uid, privFollower.uid)]),
);
await expectDenied(
  "follow update denied to owner",
  await commit(privOwner.token, [followWrite(privOwner.uid, privFollower.uid)]),
);
await expectAllowed(
  "follow get allowed to follower",
  await firestoreRequest(followDocPath, privFollower.token),
);
await expectAllowed(
  "follow get allowed to owner",
  await firestoreRequest(followDocPath, privOwner.token),
);
await expectDenied(
  "follow get denied to stranger",
  await firestoreRequest(followDocPath, privStranger.token),
);
await expectAllowed(
  "follow list allowed to follower",
  await queryFollows(privFollower.token, "followerUid", privFollower.uid),
);
await expectAllowed(
  "follow list allowed to owner",
  await queryFollows(privOwner.token, "ownerUid", privOwner.uid),
);
await expectDenied(
  "follow list denied to stranger querying owner followers",
  await queryFollows(privStranger.token, "ownerUid", privOwner.uid),
);
await expectDenied(
  "follow delete denied to stranger",
  await firestoreRequest(followDocPath, privStranger.token, { method: "DELETE" }),
);
await expectAllowed(
  "follow delete allowed to follower (unfollow)",
  await firestoreRequest(followDocPath, privFollower.token, { method: "DELETE" }),
);
await expectAllowed(
  "re-create follow for owner removal test",
  await commit(privFollower.token, [followWrite(privOwner.uid, privFollower.uid)]),
);
await expectAllowed(
  "follow delete allowed to owner (remove follower)",
  await firestoreRequest(followDocPath, privOwner.token, { method: "DELETE" }),
);

// 4. Instant revocation + automatic restore on Public -> Private -> Public & blacklist enforcement
await expectAllowed(
  "re-create follow for instant revocation test",
  await commit(privFollower.token, [followWrite(privOwner.uid, privFollower.uid)]),
);
await expectAllowed(
  "create accepted friendship for privFriend",
  await commit("owner", [friendshipWrite(privOwner.uid, privFriend.uid, "ACCEPTED")]),
);
const privSharedGuidePath = `users/${privOwner.uid}/savedGuides/guide-revocation`;
await expectAllowed(
  "public owner shares ALL_FOLLOWERS guide",
  await commit(privOwner.token, [
    savedGuideWrite(privOwner.uid, "guide-revocation", {
      monumentName: "Colosseum",
      photo: "bytes",
      shareAudience: "ALL_FOLLOWERS",
    }),
  ]),
);
await expectAllowed(
  "follower can get ALL_FOLLOWERS guide while owner is public",
  await firestoreRequest(privSharedGuidePath, privFollower.token),
);
await expectAllowed(
  "follower can list ALL_FOLLOWERS guide while owner is public",
  await runSavedGuidesQuery(privFollower.token, privOwner.uid, followersFilter),
);

// Toggle Public -> Private
await expectAllowed(
  "owner toggles isPublic=false",
  await commit(privOwner.token, [
    profileWrite(privOwner.uid, privOwnerName, { isPublic: false, allowFriendRequests: true }),
  ]),
);
await expectDenied(
  "follower get immediately denied after owner becomes private",
  await firestoreRequest(privSharedGuidePath, privFollower.token),
);
await expectDenied(
  "follower list immediately denied after owner becomes private",
  await runSavedGuidesQuery(privFollower.token, privOwner.uid, followersFilter),
);
await expectAllowed(
  "follow document remains in Firestore while owner is private",
  await firestoreRequest(followDocPath, privFollower.token),
);

// Toggle Private -> Public
await expectAllowed(
  "owner toggles isPublic=true again",
  await commit(privOwner.token, [
    profileWrite(privOwner.uid, privOwnerName, { isPublic: true, allowFriendRequests: true }),
  ]),
);
await expectAllowed(
  "follower get immediately restored when owner becomes public again",
  await firestoreRequest(privSharedGuidePath, privFollower.token),
);
await expectAllowed(
  "follower list immediately restored when owner becomes public again",
  await runSavedGuidesQuery(privFollower.token, privOwner.uid, followersFilter),
);

// Owner blacklists follower -> get & list denied even while owner is public
await expectAllowed(
  "owner blacklists follower",
  await commit(privOwner.token, [blockedUserWrite(privOwner.uid, privFollower.uid)]),
);
await expectDenied(
  "blacklisted follower get denied even while owner is public",
  await firestoreRequest(privSharedGuidePath, privFollower.token),
);
await expectDenied(
  "blacklisted follower list denied even while owner is public",
  await runSavedGuidesQuery(privFollower.token, privOwner.uid, followersFilter),
);

// Owner blacklists accepted friend -> accepted friend get & list denied by !isBlockedBy
await expectAllowed(
  "accepted friend can read ALL_FOLLOWERS guide before being blacklisted",
  await firestoreRequest(privSharedGuidePath, privFriend.token),
);
await expectAllowed(
  "owner blacklists accepted friend",
  await commit(privOwner.token, [blockedUserWrite(privOwner.uid, privFriend.uid)]),
);
await expectDenied(
  "blacklisted accepted friend get denied",
  await firestoreRequest(privSharedGuidePath, privFriend.token),
);
await expectDenied(
  "blacklisted accepted friend list denied",
  await runSavedGuidesQuery(privFriend.token, privOwner.uid, broadcastFilter),
);

// 5. blockedUsers CRUD & validation
const blockPath = `users/${privOwner.uid}/blockedUsers/${privOther.uid}`;
await expectAllowed(
  "owner can create blockedUsers entry",
  await commit(privOwner.token, [blockedUserWrite(privOwner.uid, privOther.uid)]),
);
await expectAllowed(
  "owner can get own blockedUsers entry",
  await firestoreRequest(blockPath, privOwner.token),
);
await expectAllowed(
  "owner can list own blockedUsers collection",
  await queryBlockedUsers(privOwner.token, privOwner.uid),
);
await expectDenied(
  "blocked user cannot get blockedUsers entry",
  await firestoreRequest(blockPath, privOther.token),
);
await expectDenied(
  "blocked user cannot list owner blockedUsers collection",
  await queryBlockedUsers(privOther.token, privOwner.uid),
);
await expectDenied(
  "blocked user cannot delete own blockedUsers entry",
  await firestoreRequest(blockPath, privOther.token, { method: "DELETE" }),
);
await expectDenied(
  "blocked user cannot create blockedUsers entry under owner",
  await commit(privOther.token, [blockedUserWrite(privOwner.uid, privStranger.uid)]),
);
await expectDenied(
  "stranger cannot get owner blockedUsers entry",
  await firestoreRequest(blockPath, privStranger.token),
);
await expectDenied(
  "stranger cannot list owner blockedUsers collection",
  await queryBlockedUsers(privStranger.token, privOwner.uid),
);
await expectDenied(
  "stranger cannot create blockedUsers entry under owner",
  await commit(privStranger.token, [blockedUserWrite(privOwner.uid, privStranger.uid)]),
);
await expectDenied(
  "stranger cannot delete owner blockedUsers entry",
  await firestoreRequest(blockPath, privStranger.token, { method: "DELETE" }),
);
await expectDenied(
  "self-block denied",
  await commit(privOwner.token, [blockedUserWrite(privOwner.uid, privOwner.uid)]),
);
await expectDenied(
  "blockedUsers update denied",
  await commit(privOwner.token, [blockedUserWrite(privOwner.uid, privOther.uid)]),
);
await expectDenied(
  "blockedUsers with mismatched blockedUid denied",
  await commit(privOwner.token, [
    blockedUserWrite(privOwner.uid, privStranger.uid, { blockedUid: privOther.uid }),
  ]),
);
await expectDenied(
  "blockedUsers with extra fields denied",
  await commit(privOwner.token, [
    blockedUserWrite(privOwner.uid, privStranger.uid, {
      blockedUid: privStranger.uid,
      reason: "spam",
    }),
  ]),
);
await expectAllowed(
  "owner can delete own blockedUsers entry",
  await firestoreRequest(blockPath, privOwner.token, { method: "DELETE" }),
);

// --- PR #284: users/{ownerUid}/wishlist/{monumentKey} rules tests ---

function wishlistWrite(ownerUid, monumentKey, data) {
  return {
    update: {
      name: documentName(`users/${ownerUid}/wishlist/${monumentKey}`),
      fields: fields(data),
    },
  };
}

async function queryWishlist(token, ownerUid) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers.Authorization = `Bearer ${token}`;
  return fetch(`${firestoreBase}/users/${ownerUid}:runQuery`, {
    method: "POST",
    headers,
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: "wishlist" }],
      },
    }),
  });
}

async function unauthFirestoreRequest(path, options = {}) {
  const url = path.startsWith(":") ? `${firestoreBase}${path}` : `${firestoreBase}/${path}`;
  return fetch(url, {
    ...options,
    headers: {
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers,
    },
  });
}

const wlOwner = await createUser();
const wlFriend = await createUser();
const wlPending = await createUser();
const wlFollower = await createUser();
const wlStranger = await createUser();

const wlOwnerName = `WlOwn_${wlOwner.uid.slice(0, 8)}`;
const wlFriendName = `WlFrd_${wlFriend.uid.slice(0, 8)}`;
const wlPendingName = `WlPnd_${wlPending.uid.slice(0, 8)}`;
const wlFollowerName = `WlFol_${wlFollower.uid.slice(0, 8)}`;

await expectAllowed(
  "register wlOwner profile as public",
  await commit(wlOwner.token, [
    profileWrite(wlOwner.uid, wlOwnerName, { isPublic: true, allowFriendRequests: true }),
    claimWrite(wlOwner.uid, wlOwnerName),
  ]),
);
await expectAllowed(
  "register wlFriend profile",
  await commit(wlFriend.token, [
    profileWrite(wlFriend.uid, wlFriendName, { isPublic: false, allowFriendRequests: true }),
    claimWrite(wlFriend.uid, wlFriendName),
  ]),
);
await expectAllowed(
  "register wlPending profile",
  await commit(wlPending.token, [
    profileWrite(wlPending.uid, wlPendingName, { isPublic: false, allowFriendRequests: true }),
    claimWrite(wlPending.uid, wlPendingName),
  ]),
);
await expectAllowed(
  "register wlFollower profile",
  await commit(wlFollower.token, [
    profileWrite(wlFollower.uid, wlFollowerName, { isPublic: false, allowFriendRequests: true }),
    claimWrite(wlFollower.uid, wlFollowerName),
  ]),
);

// Establish accepted friendship (wlOwner <-> wlFriend), pending request (wlOwner <-> wlPending), and follow (wlFollower -> wlOwner)
await expectAllowed(
  "set up wishlist test relationships",
  await commit("owner", [
    friendshipWrite(wlOwner.uid, wlFriend.uid, "ACCEPTED"),
    friendshipWrite(wlPending.uid, wlOwner.uid, "PENDING"),
  ]),
);
await expectAllowed(
  "wlFollower follows public wlOwner",
  await commit(wlFollower.token, [followWrite(wlOwner.uid, wlFollower.uid)]),
);

const wlKeyNoLoc = `ref_${wlFriend.uid}~guide-noloc`;
const wlKeyLoc = "48858_2294";
const wlBaseItem = (monumentKey = wlKeyLoc, overrides = {}) => ({
  monumentKey,
  sourceOwnerUid: wlFriend.uid,
  sourceGuideId: "guide-eiffel",
  monumentName: "Eiffel Tower",
  addedAt: 1700000000000,
  ...overrides,
});

// 1. Owner CRUD
const wlNoLocPath = `users/${wlOwner.uid}/wishlist/${wlKeyNoLoc}`;
const wlLocPath = `users/${wlOwner.uid}/wishlist/${wlKeyLoc}`;

await expectAllowed(
  "wishlist owner can create item without location",
  await commit(wlOwner.token, [wishlistWrite(wlOwner.uid, wlKeyNoLoc, wlBaseItem(wlKeyNoLoc))]),
);
await expectAllowed(
  "wishlist owner can create item with valid location",
  await commit(wlOwner.token, [
    wishlistWrite(
      wlOwner.uid,
      wlKeyLoc,
      wlBaseItem(wlKeyLoc, { location: { latitude: 48.8584, longitude: 2.2945 } }),
    ),
  ]),
);
await expectAllowed(
  "wishlist owner can update item",
  await commit(wlOwner.token, [
    wishlistWrite(
      wlOwner.uid,
      wlKeyLoc,
      wlBaseItem(wlKeyLoc, {
        monumentName: "Tour Eiffel",
        location: { latitude: 48.8584, longitude: 2.2945 },
      }),
    ),
  ]),
);
await expectAllowed(
  "wishlist owner can get own item",
  await firestoreRequest(wlLocPath, wlOwner.token),
);
await expectAllowed(
  "wishlist owner can list own wishlist",
  await queryWishlist(wlOwner.token, wlOwner.uid),
);
await expectAllowed(
  "wishlist owner can delete own item",
  await firestoreRequest(wlNoLocPath, wlOwner.token, { method: "DELETE" }),
);

// 2. Schema validation (all denied)
await expectDenied(
  "wishlist rejects extra photo bytes field",
  await commit(wlOwner.token, [
    wishlistWrite(
      wlOwner.uid,
      "schema-photo",
      wlBaseItem("schema-photo", { photo: new Uint8Array([1, 2, 3, 4]) }),
    ),
  ]),
);
await expectDenied(
  "wishlist rejects extra unknown field (history)",
  await commit(wlOwner.token, [
    wishlistWrite(
      wlOwner.uid,
      "schema-history",
      wlBaseItem("schema-history", { history: "Built in 1889." }),
    ),
  ]),
);
await expectDenied(
  "wishlist rejects missing monumentName",
  await commit(wlOwner.token, [
    wishlistWrite(wlOwner.uid, "schema-no-name", {
      monumentKey: "schema-no-name",
      sourceOwnerUid: wlFriend.uid,
      sourceGuideId: "guide-1",
      addedAt: 1700000000000,
    }),
  ]),
);
await expectDenied(
  "wishlist rejects missing addedAt",
  await commit(wlOwner.token, [
    wishlistWrite(wlOwner.uid, "schema-no-time", {
      monumentKey: "schema-no-time",
      sourceOwnerUid: wlFriend.uid,
      sourceGuideId: "guide-1",
      monumentName: "Louvre",
    }),
  ]),
);
await expectDenied(
  "wishlist rejects mismatched monumentKey field vs document ID",
  await commit(wlOwner.token, [
    wishlistWrite(wlOwner.uid, "schema-doc-id", wlBaseItem("different-key")),
  ]),
);
await expectDenied(
  "wishlist rejects invalid monumentKey document ID with dot",
  await commit(wlOwner.token, [
    wishlistWrite(wlOwner.uid, "invalid.key", wlBaseItem("invalid.key")),
  ]),
);
await expectDenied(
  "wishlist rejects invalid monumentKey document ID with space",
  await commit(wlOwner.token, [
    wishlistWrite(wlOwner.uid, "invalid key", wlBaseItem("invalid key")),
  ]),
);
await expectDenied(
  "wishlist rejects empty sourceGuideId",
  await commit(wlOwner.token, [
    wishlistWrite(wlOwner.uid, "schema-empty-gid", wlBaseItem("schema-empty-gid", { sourceGuideId: "" })),
  ]),
);
await expectDenied(
  "wishlist rejects sourceGuideId > 128 chars",
  await commit(wlOwner.token, [
    wishlistWrite(
      wlOwner.uid,
      "schema-long-gid",
      wlBaseItem("schema-long-gid", { sourceGuideId: "g".repeat(129) }),
    ),
  ]),
);
await expectDenied(
  "wishlist rejects empty monumentName",
  await commit(wlOwner.token, [
    wishlistWrite(wlOwner.uid, "schema-empty-name", wlBaseItem("schema-empty-name", { monumentName: "" })),
  ]),
);
await expectDenied(
  "wishlist rejects monumentName > 200 chars",
  await commit(wlOwner.token, [
    wishlistWrite(
      wlOwner.uid,
      "schema-long-name",
      wlBaseItem("schema-long-name", { monumentName: "M".repeat(201) }),
    ),
  ]),
);
await expectDenied(
  "wishlist rejects empty sourceOwnerUid",
  await commit(wlOwner.token, [
    wishlistWrite(wlOwner.uid, "schema-empty-owner", wlBaseItem("schema-empty-owner", { sourceOwnerUid: "" })),
  ]),
);
await expectDenied(
  "wishlist rejects invalid sourceOwnerUid characters",
  await commit(wlOwner.token, [
    wishlistWrite(
      wlOwner.uid,
      "schema-bad-owner",
      wlBaseItem("schema-bad-owner", { sourceOwnerUid: "bad/owner" }),
    ),
  ]),
);
await expectDenied(
  "wishlist rejects negative addedAt",
  await commit(wlOwner.token, [
    wishlistWrite(wlOwner.uid, "schema-neg-time", wlBaseItem("schema-neg-time", { addedAt: -1 })),
  ]),
);
await expectDenied(
  "wishlist rejects non-integer addedAt",
  await commit(wlOwner.token, [
    wishlistWrite(wlOwner.uid, "schema-str-time", wlBaseItem("schema-str-time", { addedAt: "1700000" })),
  ]),
);
await expectDenied(
  "wishlist rejects location with latitude > 90",
  await commit(wlOwner.token, [
    wishlistWrite(
      wlOwner.uid,
      "schema-bad-lat",
      wlBaseItem("schema-bad-lat", { location: { latitude: 95, longitude: 2.0 } }),
    ),
  ]),
);
await expectDenied(
  "wishlist rejects location with longitude < -180",
  await commit(wlOwner.token, [
    wishlistWrite(
      wlOwner.uid,
      "schema-bad-lng",
      wlBaseItem("schema-bad-lng", { location: { latitude: 48.0, longitude: -181 } }),
    ),
  ]),
);
await expectDenied(
  "wishlist rejects location missing longitude",
  await commit(wlOwner.token, [
    wishlistWrite(
      wlOwner.uid,
      "schema-missing-lng",
      wlBaseItem("schema-missing-lng", { location: { latitude: 48.0 } }),
    ),
  ]),
);
await expectDenied(
  "wishlist rejects location with extra key",
  await commit(wlOwner.token, [
    wishlistWrite(
      wlOwner.uid,
      "schema-extra-loc",
      wlBaseItem("schema-extra-loc", {
        location: { latitude: 48.0, longitude: 2.0, altitude: 100 },
      }),
    ),
  ]),
);

// 3. Friend & Blocklist matrix
await expectAllowed(
  "accepted non-blocked friend can get owner wishlist item",
  await firestoreRequest(wlLocPath, wlFriend.token),
);
await expectAllowed(
  "accepted non-blocked friend can list owner wishlist",
  await queryWishlist(wlFriend.token, wlOwner.uid),
);
await expectDenied(
  "accepted friend cannot create item in owner wishlist",
  await commit(wlFriend.token, [wishlistWrite(wlOwner.uid, "friend-create", wlBaseItem("friend-create"))]),
);
await expectDenied(
  "accepted friend cannot update item in owner wishlist",
  await commit(wlFriend.token, [
    wishlistWrite(wlOwner.uid, wlKeyLoc, wlBaseItem(wlKeyLoc, { monumentName: "Hijacked" })),
  ]),
);
await expectDenied(
  "accepted friend cannot delete item in owner wishlist",
  await firestoreRequest(wlLocPath, wlFriend.token, { method: "DELETE" }),
);

await expectDenied(
  "pending friend cannot get owner wishlist item",
  await firestoreRequest(wlLocPath, wlPending.token),
);
await expectDenied(
  "pending friend cannot list owner wishlist",
  await queryWishlist(wlPending.token, wlOwner.uid),
);

// Block accepted friend while friendship document still exists
await expectAllowed(
  "wlOwner blocks accepted friend",
  await commit(wlOwner.token, [blockedUserWrite(wlOwner.uid, wlFriend.uid)]),
);
await expectDenied(
  "blocked accepted friend immediately denied get on owner wishlist",
  await firestoreRequest(wlLocPath, wlFriend.token),
);
await expectDenied(
  "blocked accepted friend immediately denied list on owner wishlist",
  await queryWishlist(wlFriend.token, wlOwner.uid),
);

// 4. Public / Follower / Revocation matrix
await expectAllowed(
  "follower of public owner can get wishlist item",
  await firestoreRequest(wlLocPath, wlFollower.token),
);
await expectAllowed(
  "follower of public owner can list wishlist",
  await queryWishlist(wlFollower.token, wlOwner.uid),
);
await expectDenied(
  "follower cannot create item in owner wishlist",
  await commit(wlFollower.token, [
    wishlistWrite(wlOwner.uid, "follower-create", wlBaseItem("follower-create")),
  ]),
);
await expectDenied(
  "follower cannot update item in owner wishlist",
  await commit(wlFollower.token, [
    wishlistWrite(wlOwner.uid, wlKeyLoc, wlBaseItem(wlKeyLoc, { monumentName: "Hijacked" })),
  ]),
);
await expectDenied(
  "follower cannot delete item in owner wishlist",
  await firestoreRequest(wlLocPath, wlFollower.token, { method: "DELETE" }),
);

// Owner toggles Public -> Private -> immediately revoked
await expectAllowed(
  "wlOwner toggles account to private",
  await commit(wlOwner.token, [
    profileWrite(wlOwner.uid, wlOwnerName, { isPublic: false, allowFriendRequests: true }),
  ]),
);
await expectDenied(
  "follower immediately denied get after owner becomes private",
  await firestoreRequest(wlLocPath, wlFollower.token),
);
await expectDenied(
  "follower immediately denied list after owner becomes private",
  await queryWishlist(wlFollower.token, wlOwner.uid),
);

// Owner toggles back to Public, then blocks follower -> immediately denied
await expectAllowed(
  "wlOwner toggles account back to public",
  await commit(wlOwner.token, [
    profileWrite(wlOwner.uid, wlOwnerName, { isPublic: true, allowFriendRequests: true }),
  ]),
);
await expectAllowed(
  "wlOwner blocks follower while public",
  await commit(wlOwner.token, [blockedUserWrite(wlOwner.uid, wlFollower.uid)]),
);
await expectDenied(
  "blocked follower immediately denied get while owner is public",
  await firestoreRequest(wlLocPath, wlFollower.token),
);
await expectDenied(
  "blocked follower immediately denied list while owner is public",
  await queryWishlist(wlFollower.token, wlOwner.uid),
);

// 5. Stranger & unauthenticated
await expectDenied(
  "stranger cannot get owner wishlist item",
  await firestoreRequest(wlLocPath, wlStranger.token),
);
await expectDenied(
  "stranger cannot list owner wishlist",
  await queryWishlist(wlStranger.token, wlOwner.uid),
);
await expectDenied(
  "stranger cannot create owner wishlist item",
  await commit(wlStranger.token, [
    wishlistWrite(wlOwner.uid, "stranger-create", wlBaseItem("stranger-create")),
  ]),
);
await expectDenied(
  "stranger cannot delete owner wishlist item",
  await firestoreRequest(wlLocPath, wlStranger.token, { method: "DELETE" }),
);

await expectDenied(
  "unauthenticated client cannot get wishlist item",
  await unauthFirestoreRequest(wlLocPath),
);
await expectDenied(
  "unauthenticated client cannot list wishlist",
  await queryWishlist(null, wlOwner.uid),
);
await expectDenied(
  "unauthenticated client cannot create wishlist item",
  await unauthFirestoreRequest(":commit", {
    method: "POST",
    body: JSON.stringify({
      writes: [wishlistWrite(wlOwner.uid, "unauth-create", wlBaseItem("unauth-create"))],
    }),
  }),
);
await expectDenied(
  "unauthenticated client cannot delete wishlist item",
  await unauthFirestoreRequest(wlLocPath, { method: "DELETE" }),
);





// --- publicProfiles verifiedMonumentCount and users/{uid}/verifiedMonuments rules tests ---

function verifiedMonumentWrite(uid, monumentId, data = {}) {
  return {
    update: {
      name: documentName(`users/${uid}/verifiedMonuments/${monumentId}`),
      fields: fields(data),
    },
  };
}

function verifiedMonumentCountWrite(uid, count) {
  return {
    update: {
      name: documentName(`publicProfiles/${uid}`),
      fields: fields({ verifiedMonumentCount: count }),
    },
    updateMask: { fieldPaths: ["verifiedMonumentCount"] },
  };
}

function leaderboardStateWrite(uid, lastVerifiedMonumentId) {
  return {
    update: {
      name: documentName(`users/${uid}/leaderboard/state`),
      fields: fields({ lastVerifiedMonumentId }),
    },
  };
}

function usernameOnlyProfileWrite(uid, username) {
  return {
    update: {
      name: documentName(`publicProfiles/${uid}`),
      fields: fields({ uid, username, usernameLower: username.toLowerCase() }),
    },
    updateMask: { fieldPaths: ["uid", "username", "usernameLower"] },
  };
}

const counter = await createUser();
const rival = await createUser();
const counterMonumentPath = `users/${counter.uid}/verifiedMonuments/48858_2294`;
const counterStatePath = `users/${counter.uid}/leaderboard/state`;

await expectAllowed(
  "counter registers a profile",
  await commit(counter.token, [profileWrite(counter.uid, "Counter"), claimWrite(counter.uid, "Counter")]),
);
await expectAllowed(
  "rival registers a profile",
  await commit(rival.token, [profileWrite(rival.uid, "Rival"), claimWrite(rival.uid, "Rival")]),
);
await expectDenied(
  "profile cannot be created with a verified monument count",
  await commit(stranger.token, [
    {
      update: {
        name: documentName(`publicProfiles/${stranger.uid}`),
        fields: fields({
          uid: stranger.uid,
          username: "Stranger",
          usernameLower: "stranger",
          verifiedMonumentCount: 100,
        }),
      },
    },
    claimWrite(stranger.uid, "Stranger"),
  ]),
);

await expectAllowed(
  "first verified monument is recorded with its pointer and an increment to 1",
  await commit(counter.token, [
    verifiedMonumentWrite(counter.uid, "48858_2294"),
    leaderboardStateWrite(counter.uid, "48858_2294"),
    verifiedMonumentCountWrite(counter.uid, 1),
  ]),
);
await expectAllowed(
  "owner can read their counted monument",
  await firestoreRequest(counterMonumentPath, counter.token),
);
await expectDenied(
  "another user cannot read a counted monument",
  await firestoreRequest(counterMonumentPath, rival.token),
);
await expectAllowed(
  "owner can read their leaderboard pointer",
  await firestoreRequest(counterStatePath, counter.token),
);
await expectDenied(
  "another user cannot read the leaderboard pointer",
  await firestoreRequest(counterStatePath, rival.token),
);
await expectDenied(
  "a counted monument cannot be recorded again",
  await commit(counter.token, [
    verifiedMonumentWrite(counter.uid, "48858_2294"),
    leaderboardStateWrite(counter.uid, "48858_2294"),
    verifiedMonumentCountWrite(counter.uid, 2),
  ]),
);
await expectDenied(
  "a counted monument cannot be created without an increment",
  await commit(counter.token, [verifiedMonumentWrite(counter.uid, "48874_2295")]),
);
await expectDenied(
  "a counted monument and its pointer cannot be created without an increment",
  await commit(counter.token, [
    verifiedMonumentWrite(counter.uid, "48874_2295"),
    leaderboardStateWrite(counter.uid, "48874_2295"),
  ]),
);
await expectDenied(
  "the count cannot be incremented without a new counted monument",
  await commit(counter.token, [verifiedMonumentCountWrite(counter.uid, 2)]),
);
await expectDenied(
  "the count and pointer cannot change without the counted monument they name",
  await commit(counter.token, [
    leaderboardStateWrite(counter.uid, "48874_2295"),
    verifiedMonumentCountWrite(counter.uid, 2),
  ]),
);
await expectDenied(
  "a pointer to an already counted monument does not allow another increment",
  await commit(counter.token, [
    leaderboardStateWrite(counter.uid, "48858_2294"),
    verifiedMonumentCountWrite(counter.uid, 2),
  ]),
);
await expectDenied(
  "a new counted monument needs the pointer to name it",
  await commit(counter.token, [
    verifiedMonumentWrite(counter.uid, "48874_2295"),
    verifiedMonumentCountWrite(counter.uid, 2),
  ]),
);
await expectDenied(
  "one increment cannot create two counted monuments",
  await commit(counter.token, [
    verifiedMonumentWrite(counter.uid, "48874_2295"),
    verifiedMonumentWrite(counter.uid, "48880_2300"),
    leaderboardStateWrite(counter.uid, "48874_2295"),
    verifiedMonumentCountWrite(counter.uid, 2),
  ]),
);
await expectDenied(
  "the pointer cannot be written on its own",
  await commit(counter.token, [leaderboardStateWrite(counter.uid, "48874_2295")]),
);
await expectDenied(
  "the pointer cannot be deleted",
  await firestoreRequest(counterStatePath, counter.token, { method: "DELETE" }),
);
await expectDenied(
  "the count cannot jump by more than one",
  await commit(counter.token, [
    verifiedMonumentWrite(counter.uid, "48874_2295"),
    leaderboardStateWrite(counter.uid, "48874_2295"),
    verifiedMonumentCountWrite(counter.uid, 3),
  ]),
);
await expectDenied(
  "a counted monument cannot hold data",
  await commit(counter.token, [
    verifiedMonumentWrite(counter.uid, "48874_2295", { latitude: 48.8738 }),
    leaderboardStateWrite(counter.uid, "48874_2295"),
    verifiedMonumentCountWrite(counter.uid, 2),
  ]),
);
await expectDenied(
  "a counted monument ID must be a monument key",
  await commit(counter.token, [
    verifiedMonumentWrite(counter.uid, "saved-guide-id"),
    leaderboardStateWrite(counter.uid, "saved-guide-id"),
    verifiedMonumentCountWrite(counter.uid, 2),
  ]),
);
await expectDenied(
  "another user cannot record a monument for the owner",
  await commit(rival.token, [
    verifiedMonumentWrite(counter.uid, "48874_2295"),
    leaderboardStateWrite(counter.uid, "48874_2295"),
    verifiedMonumentCountWrite(counter.uid, 2),
  ]),
);
await expectDenied(
  "another user cannot change the owner's count",
  await commit(rival.token, [verifiedMonumentCountWrite(counter.uid, 2)]),
);
await expectDenied(
  "a counted monument cannot be deleted",
  await firestoreRequest(counterMonumentPath, counter.token, { method: "DELETE" }),
);
await expectDenied(
  "a username change cannot reset the count",
  await commit(counter.token, [
    profileWrite(counter.uid, "CounterNew"),
    claimWrite(counter.uid, "CounterNew"),
    { delete: documentName("usernameClaims/counter") },
  ]),
);
await expectAllowed(
  "a username change keeps the count",
  await commit(counter.token, [
    usernameOnlyProfileWrite(counter.uid, "CounterNew"),
    claimWrite(counter.uid, "CounterNew"),
    { delete: documentName("usernameClaims/counter") },
  ]),
);
await expectAllowed(
  "a distinct monument with its pointer increments the count to 2",
  await commit(counter.token, [
    verifiedMonumentWrite(counter.uid, "48874_2295"),
    leaderboardStateWrite(counter.uid, "48874_2295"),
    verifiedMonumentCountWrite(counter.uid, 2),
  ]),
);
const counterProfile = await expectAllowed(
  "any signed-in user can read the verified monument count on a public profile",
  await firestoreRequest(`publicProfiles/${counter.uid}`, rival.token),
);
if (
  counterProfile.fields.verifiedMonumentCount.integerValue !== "2" ||
  counterProfile.fields.username.stringValue !== "CounterNew"
) {
  throw new Error(`Unexpected counter profile: ${JSON.stringify(counterProfile.fields)}`);
}
console.log("PASS the profile keeps the username and a count of 2");
