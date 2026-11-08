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
  if (typeof value === "string") return { stringValue: value };
  if (Array.isArray(value)) return { arrayValue: { values: value.map(toFirestoreValue) } };
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
