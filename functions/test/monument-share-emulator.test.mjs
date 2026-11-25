import assert from "node:assert/strict";
import test from "node:test";
import { randomUUID } from "node:crypto";
import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { processMonumentShareWrite } from "../lib/index.js";

const app = getApps()[0] ?? initializeApp({ projectId: process.env.GCLOUD_PROJECT });
const firestore = getFirestore(app);

test("sends a newly shared selected monument to the recipient installation", async () => {
  const ownerUid = `owner-${randomUUID()}`;
  const recipientUid = `recipient-${randomUUID()}`;
  const guideId = `guide-${randomUUID()}`;

  await Promise.all([
    firestore.collection("publicProfiles").doc(ownerUid).set({
      uid: ownerUid,
      username: "Alice",
      usernameLower: "alice",
    }),
    firestore
      .collection("notificationInstallations")
      .doc(recipientUid)
      .collection("installations")
      .doc("recipient-fid")
      .set({ fid: "recipient-fid" }),
  ]);

  const sentMessages = [];
  await processMonumentShareWrite(
    ownerUid,
    guideId,
    undefined,
    {
      monumentName: "The Tower",
      shareAudience: "SELECTED_FRIENDS",
      sharedWithUids: [recipientUid],
    },
    {
      firestore,
      sendEachForMulticast: async (message) => {
        sentMessages.push(message);
        return { responses: [{ success: true }] };
      },
    },
  );

  assert.deepEqual(sentMessages, [
    {
      tokens: ["recipient-fid"],
      data: {
        type: "monument_share",
        guideId,
        senderUid: ownerUid,
        senderUsername: "Alice",
        recipientUid,
        monumentName: "The Tower",
      },
      android: { priority: "high", ttl: 60 * 60 * 1000 },
    },
  ]);
});

test("does not resend an already granted share", async () => {
  const ownerUid = `owner-${randomUUID()}`;
  const recipientUid = `recipient-${randomUUID()}`;
  const sentMessages = [];

  await processMonumentShareWrite(
    ownerUid,
    "guide-existing",
    {
      monumentName: "The Tower",
      shareAudience: "SELECTED_FRIENDS",
      sharedWithUids: [recipientUid],
    },
    {
      monumentName: "The Tower",
      shareAudience: "SELECTED_FRIENDS",
      sharedWithUids: [recipientUid],
    },
    {
      firestore,
      sendEachForMulticast: async (message) => {
        sentMessages.push(message);
        return { responses: [] };
      },
    },
  );

  assert.deepEqual(sentMessages, []);
});

test("sends an all-friends share only to accepted friends", async () => {
  const ownerUid = `owner-${randomUUID()}`;
  const recipientUid = `recipient-${randomUUID()}`;
  const pendingUid = `pending-${randomUUID()}`;
  const guideId = `guide-${randomUUID()}`;

  await Promise.all([
    firestore.collection("publicProfiles").doc(ownerUid).set({ username: "Alice" }),
    firestore
      .collection("friendships")
      .doc(`${ownerUid}~${recipientUid}`)
      .set({ participantUids: [ownerUid, recipientUid], status: "ACCEPTED" }),
    firestore
      .collection("friendships")
      .doc(`${ownerUid}~${pendingUid}`)
      .set({ participantUids: [ownerUid, pendingUid], status: "PENDING" }),
    firestore
      .collection("notificationInstallations")
      .doc(recipientUid)
      .collection("installations")
      .doc("recipient-fid")
      .set({ fid: "recipient-fid" }),
  ]);

  const sentMessages = [];
  await processMonumentShareWrite(
    ownerUid,
    guideId,
    undefined,
    { monumentName: "The Tower", shareAudience: "ALL_FRIENDS" },
    {
      firestore,
      sendEachForMulticast: async (message) => {
        sentMessages.push(message);
        return { responses: [{ success: true }] };
      },
    },
  );

  assert.deepEqual(sentMessages.map(({ tokens, data }) => ({ tokens, data })), [
    {
      tokens: ["recipient-fid"],
      data: {
        type: "monument_share",
        guideId,
        senderUid: ownerUid,
        senderUsername: "Alice",
        recipientUid,
        monumentName: "The Tower",
      },
    },
  ]);
});

test("does not resend a selected recipient when switching to all friends", async () => {
  const ownerUid = `owner-${randomUUID()}`;
  const recipientUid = `recipient-${randomUUID()}`;
  const guideId = `guide-${randomUUID()}`;

  await Promise.all([
    firestore.collection("publicProfiles").doc(ownerUid).set({ username: "Alice" }),
    firestore
      .collection("friendships")
      .doc(`${ownerUid}~${recipientUid}`)
      .set({ participantUids: [ownerUid, recipientUid], status: "ACCEPTED" }),
    firestore
      .collection("notificationInstallations")
      .doc(recipientUid)
      .collection("installations")
      .doc("recipient-fid")
      .set({ fid: "recipient-fid" }),
  ]);

  const sentMessages = [];
  await processMonumentShareWrite(
    ownerUid,
    guideId,
    {
      monumentName: "The Tower",
      shareAudience: "SELECTED_FRIENDS",
      sharedWithUids: [recipientUid],
    },
    { monumentName: "The Tower", shareAudience: "ALL_FRIENDS" },
    {
      firestore,
      sendEachForMulticast: async (message) => {
        sentMessages.push(message);
        return { responses: [{ success: true }] };
      },
    },
  );

  assert.deepEqual(sentMessages, []);
});

test("does not notify when a share is downgraded from all friends", async () => {
  const ownerUid = `owner-${randomUUID()}`;
  const recipientUid = `recipient-${randomUUID()}`;
  const sentMessages = [];

  await processMonumentShareWrite(
    ownerUid,
    "guide-downgrade",
    { monumentName: "The Tower", shareAudience: "ALL_FRIENDS" },
    {
      monumentName: "The Tower",
      shareAudience: "SELECTED_FRIENDS",
      sharedWithUids: [recipientUid],
    },
    {
      firestore,
      sendEachForMulticast: async (message) => {
        sentMessages.push(message);
        return { responses: [] };
      },
    },
  );

  assert.deepEqual(sentMessages, []);
});

test("does not notify when a saved guide is deleted", async () => {
  const sentMessages = [];

  await processMonumentShareWrite(
    `owner-${randomUUID()}`,
    "guide-deleted",
    { monumentName: "The Tower", shareAudience: "SELECTED_FRIENDS" },
    undefined,
    {
      firestore,
      sendEachForMulticast: async (message) => {
        sentMessages.push(message);
        return { responses: [] };
      },
    },
  );

  assert.deepEqual(sentMessages, []);
});

test("does not notify when a saved guide has no monument name", async () => {
  const sentMessages = [];

  await processMonumentShareWrite(
    `owner-${randomUUID()}`,
    "guide-without-name",
    undefined,
    { shareAudience: "SELECTED_FRIENDS", sharedWithUids: ["recipient"] },
    {
      firestore,
      sendEachForMulticast: async (message) => {
        sentMessages.push(message);
        return { responses: [] };
      },
    },
  );

  assert.deepEqual(sentMessages, []);
});

test("deletes stale installation IDs after a failed delivery", async () => {
  const ownerUid = `owner-${randomUUID()}`;
  const recipientUid = `recipient-${randomUUID()}`;
  const fid = `stale-${randomUUID()}`;

  await Promise.all([
    firestore.collection("publicProfiles").doc(ownerUid).set({ username: "Alice" }),
    firestore
      .collection("notificationInstallations")
      .doc(recipientUid)
      .collection("installations")
      .doc(fid)
      .set({ fid }),
  ]);

  await processMonumentShareWrite(
    ownerUid,
    `guide-${randomUUID()}`,
    undefined,
    {
      monumentName: "The Tower",
      shareAudience: "SELECTED_FRIENDS",
      sharedWithUids: [recipientUid],
    },
    {
      firestore,
      sendEachForMulticast: async () => ({
        responses: [
          {
            success: false,
            error: { code: "messaging/registration-token-not-registered" },
          },
        ],
      }),
    },
  );

  const installation = await firestore
    .collection("notificationInstallations")
    .doc(recipientUid)
    .collection("installations")
    .doc(fid)
    .get();
  assert.equal(installation.exists, false);
});
