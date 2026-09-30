package com.github.se.bootcamp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.github.se.bootcamp.model.authentication.AuthRepository
import com.github.se.bootcamp.ui.authentication.SignInScreen
import com.github.se.bootcamp.ui.map.MapScreen
import com.github.se.bootcamp.ui.navigation.NavigationActions
import com.github.se.bootcamp.ui.navigation.Screen
import com.github.se.bootcamp.ui.overview.AddTodoScreen
import com.github.se.bootcamp.ui.overview.EditToDoScreen
import com.github.se.bootcamp.ui.overview.OverviewScreen
import com.github.se.bootcamp.ui.theme.BootcampTheme
import com.google.firebase.auth.FirebaseAuth
import okhttp3.OkHttpClient

/**
 * *B3 only*:
 *
 * Provide an OkHttpClient client for network requests.
 *
 * Property `client` is mutable for testing purposes.
 */
object HttpClientProvider {
  var client: OkHttpClient = OkHttpClient()
}

class MainActivity : ComponentActivity() {

  private lateinit var auth: FirebaseAuth
  private lateinit var authRepository: AuthRepository

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContent { BootcampTheme { Surface(modifier = Modifier.fillMaxSize()) { BootcampApp() } } }
  }
}

/**
 * `BootcampApp` is the main composable function that sets up the whole app UI. It initializes the
 * navigation controller and defines the navigation graph. You can add your app implementation
 * inside this function.
 *
 * @param navHostController The navigation controller used for navigating between screens.
 *
 * For B3:
 *
 * @param context The context of the application, used for accessing resources and services.
 * @param credentialManager The CredentialManager instance for handling authentication credentials.
 */
@Composable
fun BootcampApp(
    context: Context = LocalContext.current,
    credentialManager: CredentialManager = CredentialManager.create(context),
) {
  val navController = rememberNavController()
  val navigationActions = NavigationActions(navController)
  val startDestination =
      if (FirebaseAuth.getInstance().currentUser == null) Screen.Auth.name
      else Screen.Overview.route

  NavHost(navController = navController, startDestination = startDestination) {
    navigation(
        startDestination = Screen.Auth.route,
        route = Screen.Auth.name,
    ) {
      composable(Screen.Auth.route) {
        SignInScreen(
            credentialManager = credentialManager,
            onSignedIn = { navigationActions.navigateTo(Screen.Overview) })
      }
    }

    navigation(
        startDestination = Screen.Overview.route,
        route = Screen.Overview.name,
    ) {
      composable(Screen.Overview.route) {
        OverviewScreen(
            onSelectTodo = { navigationActions.navigateTo(Screen.EditToDo(it.uid)) },
            onAddTodo = { navigationActions.navigateTo(Screen.AddToDo) },
            onSignedOut = { navigationActions.navigateTo(Screen.Auth) },
            navigationActions = navigationActions,
            credentialManager = credentialManager)
      }
      composable(Screen.AddToDo.route) {
        AddTodoScreen(
            onDone = { navigationActions.navigateTo(Screen.Overview) },
            onGoBack = { navigationActions.goBack() })
      }
      composable(Screen.EditToDo.route) { navBackStackEntry ->
        // Get the Todo UID from the arguments
        val uid = navBackStackEntry.arguments?.getString("uid")

        // Create the EditToDoScreen with the Todo UID
        uid?.let {
          EditToDoScreen(
              onDone = { navigationActions.navigateTo(Screen.Overview) },
              todoUid = it,
              onGoBack = { navigationActions.goBack() })
        }
            ?: run {
              Log.e("EditToDoScreen", "ToDo UID is null")
              Toast.makeText(context, "ToDo UID is null", Toast.LENGTH_SHORT).show()
            }
      }
    }

    navigation(
        startDestination = Screen.Map.route,
        route = Screen.Map.name,
    ) {
      composable(Screen.Map.route) { MapScreen(navigationActions = navigationActions) }
    }
  }
}
