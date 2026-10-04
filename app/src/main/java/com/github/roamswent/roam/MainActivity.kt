package com.github.roamswent.roam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.github.roamswent.roam.resources.C
import com.github.roamswent.roam.ui.home.HomeScreen
import com.github.roamswent.roam.ui.navigation.Routes
import com.github.roamswent.roam.ui.theme.SampleAppTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      SampleAppTheme {
        val navController = rememberNavController()
        Surface(
            modifier = Modifier.fillMaxSize().semantics { testTag = C.Tag.main_screen_container },
            color = MaterialTheme.colorScheme.background,
        ) {
          NavHost(navController = navController, startDestination = Routes.Home) {
            composable(Routes.Home) {
              HomeScreen(onScanClicked = { navController.navigate(Routes.Scan) })
            }
            composable(Routes.Scan) {
              Box(Modifier.fillMaxSize().semantics { testTag = C.Tag.scan_root_stub })
            }
          }
        }
      }
    }
  }
}
