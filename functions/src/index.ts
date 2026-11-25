/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore, Firestore } from "firebase-admin/firestore";
import { getMessaging, MulticastMessage } from "firebase-admin/messaging";
import { logger } from "firebase-functions";
import { onDocumentCreated, onDocumentWritten } from "firebase-functions/v2/firestore";
import {
  friendRequestMessageData,
  isPendingFriendRequest,
} from "./friend-request-message.js";
import {
  monumentShareMessageData,
  SavedGuideDocument,
  selectedRecipients,
} from "./monument-share-message.js";

if (getApps().length === 0) initializeApp();

const firestore = getFirestore();

/**
 * Sends a high-priority data push when a new pending friend-request document is created.
 *
 * Android renders the notification itself, including localized action labels, while this function
 * remains responsible only for event validation, recipient lookup, and message delivery.
 */
export const sendFriendRequestNotification = onDocumentCreated(
  "friendships/{pairId}",
  async (event) => {
    const request = event.data?.data();
    const pairId = event.params.pairId;
    if (!isPendingFriendRequest(pairId, request)) return;

    const requesterProfile = await firestore
      .collection("publicProfiles")
      .doc(request.requesterUid)
      .get();
    const requesterUsername = requesterProfile.get("username");
    if (typeof requesterUsername !== "string" || requesterUsername.length === 0) {
      throw new Error(`Requester profile ${request.requesterUid} has no username.`);
    }

    const recipientInstallations = await firestore
      .collection("notificationInstallations")
      .doc(request.recipientUid)
      .collection("installations")
      .get();
    const fids = [...new Set(recipientInstallations.docs.map((installation) => installation.id))];
    if (fids.length === 0) {
      logger.info("Friend request has no registered notification installations.", {
        recipientUid: request.recipientUid,
      });
      return;
    }

    const data = friendRequestMessageData(
      pairId,
      request.requesterUid,
      requesterUsername,
      request.recipientUid,
    );
    // FCM limits a multicast request to 500 Firebase Installation IDs.
    for (let offset = 0; offset < fids.length; offset += 500) {
      const batch = fids.slice(offset, offset + 500);
      const result = await getMessaging().sendEachForMulticast({
        tokens: batch,
        data,
        android: { priority: "high", ttl: 60 * 60 * 1000 },
      });
      const staleFids = batch.filter((_, index) => {
        const response = result.responses[index];
        return (
          !response.success &&
          (response.error?.code === "messaging/invalid-registration-token" ||
            response.error?.code === "messaging/registration-token-not-registered")
        );
      });
      await Promise.all(
        staleFids.map((fid) =>
          firestore
            .collection("notificationInstallations")
            .doc(request.recipientUid)
            .collection("installations")
            .doc(fid)
            .delete(),
        ),
      );
    }
  },
);

export const sendMonumentShareNotification = onDocumentWritten(
  "users/{ownerUid}/savedGuides/{guideId}",
  async (event) => {
    await processMonumentShareWrite(
      event.params.ownerUid,
      event.params.guideId,
      event.data?.before.data() as SavedGuideDocument | undefined,
      event.data?.after.data() as SavedGuideDocument | undefined,
    );
  },
);

export async function processMonumentShareWrite(
  ownerUid: string,
  guideId: string,
  before: SavedGuideDocument | undefined,
  after: SavedGuideDocument | undefined,
  dependencies: NotificationDependencies = defaultNotificationDependencies(),
): Promise<void> {
  if (!after) return;

  const monumentName = after.monumentName;
  if (typeof monumentName !== "string" || monumentName.length === 0) return;

  const recipients = await newlySharedRecipients(dependencies.firestore, ownerUid, before, after);
  if (recipients.length === 0) return;

  const ownerProfile = await dependencies.firestore
    .collection("publicProfiles")
    .doc(ownerUid)
    .get();
  const senderUsername = ownerProfile.get("username");
  if (typeof senderUsername !== "string" || senderUsername.length === 0) {
    throw new Error(`Owner profile ${ownerUid} has no username.`);
  }

  await Promise.all(
    recipients.map((recipientUid) =>
      sendToRecipient(
        dependencies.firestore,
        dependencies.sendEachForMulticast,
        recipientUid,
        monumentShareMessageData(
          guideId,
          ownerUid,
          senderUsername,
          recipientUid,
          monumentName,
        ),
      ),
    ),
  );
}

interface NotificationDependencies {
  firestore: Firestore;
  sendEachForMulticast: (message: MulticastMessage) => Promise<{
    responses: Array<{
      success: boolean;
      error?: { code?: string };
    }>;
  }>;
}

function defaultNotificationDependencies(): NotificationDependencies {
  return {
    firestore,
    sendEachForMulticast: (message) => getMessaging().sendEachForMulticast(message),
  };
}

async function newlySharedRecipients(
  firestore: Firestore,
  ownerUid: string,
  before: SavedGuideDocument | undefined,
  after: SavedGuideDocument,
): Promise<string[]> {
  if (
    before?.shareAudience === "ALL_FRIENDS" &&
    after.shareAudience !== "ALL_FRIENDS"
  ) {
    return [];
  }
  if (before?.shareAudience === after.shareAudience && after.shareAudience === "ALL_FRIENDS") {
    return [];
  }
  const previous = new Set(selectedRecipients(before));
  let current: string[] = [];
  if (after.shareAudience === "SELECTED_FRIENDS") {
    current = selectedRecipients(after);
  } else if (after.shareAudience === "ALL_FRIENDS") {
    const friendships = await firestore
      .collection("friendships")
      .where("participantUids", "array-contains", ownerUid)
      .get();
    current = friendships.docs
      .filter((friendship) => friendship.get("status") === "ACCEPTED")
      .flatMap((friendship) => {
        const participants = friendship.get("participantUids");
        return Array.isArray(participants)
          ? participants.filter((uid): uid is string => typeof uid === "string" && uid !== ownerUid)
          : [];
      });
  }
  return [...new Set(current)].filter((uid) => uid !== ownerUid && !previous.has(uid));
}

async function sendToRecipient(
  firestore: Firestore,
  sendEachForMulticast: NotificationDependencies["sendEachForMulticast"],
  recipientUid: string,
  data: Record<string, string>,
): Promise<void> {
  const installations = await firestore
    .collection("notificationInstallations")
    .doc(recipientUid)
    .collection("installations")
    .get();
  const fids = [...new Set(installations.docs.map((installation) => installation.id))];
  if (fids.length === 0) return;

  for (let offset = 0; offset < fids.length; offset += 500) {
    const batch = fids.slice(offset, offset + 500);
    const result = await sendEachForMulticast({
      tokens: batch,
      data,
      android: { priority: "high", ttl: 60 * 60 * 1000 },
    });
    const staleFids = batch.filter((_, index) => {
      const response = result.responses[index];
      return (
        !response.success &&
        (response.error?.code === "messaging/invalid-registration-token" ||
          response.error?.code === "messaging/registration-token-not-registered")
      );
    });
    await Promise.all(
      staleFids.map((fid) =>
        firestore
          .collection("notificationInstallations")
          .doc(recipientUid)
          .collection("installations")
          .doc(fid)
          .delete(),
      ),
    );
  }
}
