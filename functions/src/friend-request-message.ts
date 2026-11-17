/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

/** Firestore fields used to decide whether a friendship creation is a new request. */
export interface FriendshipDocument {
  participantUids?: unknown;
  requesterUid?: unknown;
  recipientUid?: unknown;
  status?: unknown;
}

/**
 * Returns true only for a well-formed pending request stored under its UID-pair document ID.
 *
 * Checking the relationship's direction and canonical pair ID prevents a later accepted friendship
 * or malformed record from being treated as a new friend request.
 */
export function isPendingFriendRequest(
  pairId: string,
  value: FriendshipDocument | undefined,
): value is FriendshipDocument & {
  participantUids: string[];
  requesterUid: string;
  recipientUid: string;
  status: "PENDING";
} {
  if (
    value?.status !== "PENDING" ||
    typeof value.requesterUid !== "string" ||
    typeof value.recipientUid !== "string" ||
    value.requesterUid === value.recipientUid ||
    !Array.isArray(value.participantUids) ||
    value.participantUids.length !== 2 ||
    !value.participantUids.every((uid) => typeof uid === "string")
  ) {
    return false;
  }

  const [firstUid, secondUid] = [...value.participantUids].sort();
  return (
    firstUid === value.participantUids[0] &&
    secondUid === value.participantUids[1] &&
    value.participantUids.includes(value.requesterUid) &&
    value.participantUids.includes(value.recipientUid) &&
    pairId === `${firstUid}~${secondUid}`
  );
}

/**
 * Creates the string-only FCM data fields parsed by the Android notification service.
 *
 * Keeping this mapping in one helper ensures notification types can be added without coupling the
 * Firestore trigger to Android's notification rendering.
 */
export function friendRequestMessageData(
  requestId: string,
  requesterUid: string,
  requesterUsername: string,
  recipientUid: string,
): Record<string, string> {
  return {
    type: "friend_request",
    requestId,
    requesterUid,
    requesterUsername,
    recipientUid,
  };
}
