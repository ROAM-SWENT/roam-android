// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Mistral Vibe <vibe@mistral.ai>
// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.ui.navigation

import android.net.Uri

object Routes {
  const val SignIn = "sign_in"
  const val SignUp = "sign_up"
  const val EmailVerification = "email_verification"
  const val RegisterProfile = "register_profile"
  const val Home = "home"
  const val Explore = "explore"
  const val Scan = "scan"
  const val ScanReview = "scan_review"
  const val Settings = "settings"
  const val Collection = "collection"
  const val Friends = "friends"
  const val MonumentDetail = "monument_detail"
  const val MonumentDetailGuideIdArg = "guideId"
  /** Route pattern of the saved monument detail screen, registered in the nav graph. */
  const val MonumentDetailPattern = "$MonumentDetail/{$MonumentDetailGuideIdArg}"

  /** Route that opens the saved guide identified by [guideId] in the detail screen. */
  fun monumentDetail(guideId: String): String = "$MonumentDetail/${Uri.encode(guideId)}"

  const val FriendCollection = "friend_collection"
  const val FriendUidArg = "friendUid"
  const val FriendNameArg = "friendName"
  const val FriendCollectionPattern =
      "$FriendCollection/{$FriendUidArg}?$FriendNameArg={$FriendNameArg}"

  /** Route that opens the monuments [friendUid] shares, titled with [friendName]. */
  fun friendCollection(friendUid: String, friendName: String): String =
      "$FriendCollection/${Uri.encode(friendUid)}" +
          if (friendName.isEmpty()) "" else "?$FriendNameArg=${Uri.encode(friendName)}"

  const val SharedMonumentDetail = "shared_monument_detail"
  const val SharedMonumentDetailPattern =
      "$SharedMonumentDetail/{$FriendUidArg}/{$MonumentDetailGuideIdArg}"

  /** Route that opens the monument [guideId] shared by [friendUid]. */
  fun sharedMonumentDetail(friendUid: String, guideId: String): String =
      "$SharedMonumentDetail/${Uri.encode(friendUid)}/${Uri.encode(guideId)}"
}
