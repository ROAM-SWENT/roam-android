/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

import { getApps, initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { logger } from "firebase-functions";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import {
  friendRequestMessageData,
  isPendingFriendRequest,
} from "./friend-request-message.js";

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
        fids: batch,
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
