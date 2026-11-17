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

function profileWrite(uid, username) {
  return {
    update: {
      name: documentName(`publicProfiles/${uid}`),
      fields: fields({ uid, username, usernameLower: username.toLowerCase() }),
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

async function expectAllowed(label, response) {
  const body = await response.text();
  if (!response.ok) throw new Error(`${label} failed (${response.status}): ${body}`);
  console.log(`PASS ${label}`);
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
  "requester can send a new request after rejection",
  await commit(otherUser.token, [friendshipWrite(otherUser.uid, owner.uid)]),
);
await expectAllowed(
  "requester can cancel a pending request",
  await firestoreRequest(friendshipPath, otherUser.token, { method: "DELETE" }),
);

// --- users/{ownerUid}/savedGuides/{guideId} rules tests ---

function savedGuideWrite(ownerUid, guideId, data) {
  return {
    update: {
      name: documentName(`users/${ownerUid}/savedGuides/${guideId}`),
      fields: fields(data),
    },
  };
}

const guideOwner = await createUser();
const friend = await createUser();
const unlistedFriend = await createUser();
const follower = await createUser();
const strangerUser = await createUser();

// Set up public profiles
await commit(guideOwner.token, [
  profileWrite(guideOwner.uid, `Owner_${guideOwner.uid.slice(0, 8)}`),
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

