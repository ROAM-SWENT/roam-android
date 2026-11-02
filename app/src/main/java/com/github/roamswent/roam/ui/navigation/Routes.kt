// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.ui.navigation

import android.net.Uri

object Routes {
  const val SignIn = "sign_in"
  const val Home = "home"
  const val Scan = "scan"
  const val ScanReview = "scan_review"
  const val Settings = "settings"
  const val Collection = "collection"
  const val GUIDE_ID_ARG = "guideId"
  const val GuideDetail = "guide_detail/{$GUIDE_ID_ARG}"

  fun guideDetail(guideId: String) = "guide_detail/${Uri.encode(guideId)}"
}
