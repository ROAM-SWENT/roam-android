/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.notifications

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotificationNavigationTest {
  @Test
  fun parsesFriendRequestNavigation() {
    val intent = Intent(FriendRequestNotifications.ACTION_OPEN_FRIENDS)

    assertEquals(NotificationNavigation.Friends, NotificationNavigationParser.parse(intent))
  }

  @Test
  fun parsesSharedMonumentNavigation() {
    val intent = Intent(MonumentShareNotifications.ACTION_OPEN_SHARED_MONUMENT)
    intent.putExtra(MonumentShareNotifications.EXTRA_SHARED_MONUMENT_OWNER_UID, "owner")
    intent.putExtra(MonumentShareNotifications.EXTRA_SHARED_MONUMENT_GUIDE_ID, "guide")

    assertEquals(
        NotificationNavigation.SharedMonument("owner", "guide"),
        NotificationNavigationParser.parse(intent),
    )
  }

  @Test
  fun rejectsInvalidOrUnknownNotificationIntents() {
    val missingExtra = Intent(MonumentShareNotifications.ACTION_OPEN_SHARED_MONUMENT)
    missingExtra.putExtra(MonumentShareNotifications.EXTRA_SHARED_MONUMENT_OWNER_UID, "owner")
    val missingOwner = Intent(MonumentShareNotifications.ACTION_OPEN_SHARED_MONUMENT)
    missingOwner.putExtra(MonumentShareNotifications.EXTRA_SHARED_MONUMENT_GUIDE_ID, "guide")
    val blankOwner = Intent(MonumentShareNotifications.ACTION_OPEN_SHARED_MONUMENT)
    blankOwner.putExtra(MonumentShareNotifications.EXTRA_SHARED_MONUMENT_OWNER_UID, " ")
    blankOwner.putExtra(MonumentShareNotifications.EXTRA_SHARED_MONUMENT_GUIDE_ID, "guide")
    val blankGuide = Intent(MonumentShareNotifications.ACTION_OPEN_SHARED_MONUMENT)
    blankGuide.putExtra(MonumentShareNotifications.EXTRA_SHARED_MONUMENT_OWNER_UID, "owner")
    blankGuide.putExtra(MonumentShareNotifications.EXTRA_SHARED_MONUMENT_GUIDE_ID, " ")

    assertNull(NotificationNavigationParser.parse(missingExtra))
    assertNull(NotificationNavigationParser.parse(missingOwner))
    assertNull(NotificationNavigationParser.parse(blankOwner))
    assertNull(NotificationNavigationParser.parse(blankGuide))
    assertNull(NotificationNavigationParser.parse(Intent("unknown")))
    assertNull(NotificationNavigationParser.parse(null))
  }
}
