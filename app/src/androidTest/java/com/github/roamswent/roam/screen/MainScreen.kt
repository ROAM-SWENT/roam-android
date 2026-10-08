package com.github.roamswent.roam.screen

import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import com.github.roamswent.roam.resources.C
import io.github.kakaocup.compose.node.element.ComposeScreen
import io.github.kakaocup.compose.node.element.KNode

class MainScreen(semanticsProvider: SemanticsNodeInteractionsProvider) :
    ComposeScreen<MainScreen>(
        semanticsProvider = semanticsProvider,
        viewBuilderAction = { hasTestTag(C.Tag.home_screen_container) },
    ) {

  val scanButton: KNode =
      child<KNode> {
        hasAnyAncestor(androidx.compose.ui.test.hasTestTag(C.Tag.home_screen_container))
        hasTestTag(C.Tag.home_scan_button)
      }

  val scanLabel: KNode =
      child<KNode> {
        useUnmergedTree = true
        hasAnyAncestor(androidx.compose.ui.test.hasTestTag(C.Tag.home_screen_container))
        hasTestTag(C.Tag.home_scan_label)
      }
}
