// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.navigation

import java.net.URLEncoder

object Routes {
  const val SignIn = "sign_in"
  const val Home = "home"
  const val Scan = "scan"
  const val ScanReview = "scan_review"
  const val Settings = "settings"
  const val Collection = "collection"
  const val GuideIdArg = "guideId"
  const val GuideDetail = "guide_detail/{$GuideIdArg}"

  /** The route of the saved guide [guideId], percent-encoded so any id fits in one path segment. */
  fun guideDetail(guideId: String) =
      "guide_detail/${URLEncoder.encode(guideId, Charsets.UTF_8.name()).replace("+", "%20")}"
}
