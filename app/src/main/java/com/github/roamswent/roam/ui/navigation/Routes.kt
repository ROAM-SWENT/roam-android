// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
// Co-authored-by: Mistral Vibe <vibe@mistral.ai>
package com.github.roamswent.roam.ui.navigation

import android.net.Uri

object Routes {
  const val SignIn = "sign_in"
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
}
