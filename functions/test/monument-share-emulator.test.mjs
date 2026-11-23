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
