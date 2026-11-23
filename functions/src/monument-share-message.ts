/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

export interface SavedGuideDocument {
  shareAudience?: unknown;
  sharedWithUids?: unknown;
  monumentName?: unknown;
}

export function monumentShareMessageData(
  guideId: string,
  senderUid: string,
  senderUsername: string,
  recipientUid: string,
  monumentName: string,
): Record<string, string> {
  return {
    type: "monument_share",
    guideId,
    senderUid,
    senderUsername,
    recipientUid,
    monumentName,
  };
}

export function selectedRecipients(value: SavedGuideDocument | undefined): string[] {
  if (
    value?.shareAudience !== "SELECTED_FRIENDS" ||
    !Array.isArray(value.sharedWithUids)
  ) {
    return [];
  }
  return [...new Set(value.sharedWithUids.filter((uid): uid is string => typeof uid === "string"))];
}
