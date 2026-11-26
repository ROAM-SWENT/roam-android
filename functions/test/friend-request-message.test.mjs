import assert from "node:assert/strict";
import test from "node:test";
import {
  friendRequestMessageData,
  isPendingFriendRequest,
} from "../lib/friend-request-message.js";
import {
  monumentShareMessageData,
  selectedRecipients,
} from "../lib/monument-share-message.js";

const validRequest = {
  participantUids: ["alice", "bob"],
  requesterUid: "alice",
  recipientUid: "bob",
  status: "PENDING",
};

test("recognizes a canonical pending friend request", () => {
  assert.equal(isPendingFriendRequest("alice~bob", validRequest), true);
});

test("ignores accepted, malformed, and incorrectly keyed relationships", () => {
  assert.equal(
    isPendingFriendRequest("alice~bob", { ...validRequest, status: "ACCEPTED" }),
    false,
  );
  assert.equal(isPendingFriendRequest("alice~charlie", validRequest), false);
  assert.equal(
    isPendingFriendRequest("alice~bob", { ...validRequest, participantUids: ["bob", "alice"] }),
    false,
  );
  assert.equal(isPendingFriendRequest("alice~bob", undefined), false);
});

test("produces the data contract consumed by Android", () => {
  assert.deepEqual(friendRequestMessageData("alice~bob", "alice", "Alice", "bob"), {
    type: "friend_request",
    requestId: "alice~bob",
    requesterUid: "alice",
    requesterUsername: "Alice",
    recipientUid: "bob",
  });
});

test("produces and validates the monument-share data contract", () => {
  assert.deepEqual(
    monumentShareMessageData("guide-1", "alice", "Alice", "bob", "The Tower"),
    {
      type: "monument_share",
      guideId: "guide-1",
      senderUid: "alice",
      senderUsername: "Alice",
      recipientUid: "bob",
      monumentName: "The Tower",
    },
  );
  assert.deepEqual(
    selectedRecipients({
      shareAudience: "SELECTED_FRIENDS",
      sharedWithUids: ["bob", "bob", 42, "carol"],
    }),
    ["bob", "carol"],
  );
  assert.deepEqual(selectedRecipients({ shareAudience: "ALL_FRIENDS" }), []);
});
