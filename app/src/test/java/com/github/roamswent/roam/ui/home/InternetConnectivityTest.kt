/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class InternetConnectivityTest {
  @get:Rule val composeTestRule = createComposeRule()

  private lateinit var context: Context
  private lateinit var connectivityManager: ConnectivityManager
  private val network: Network = mock()

  @Before
  fun setUp() {
    context = mock()
    connectivityManager = mock()
    whenever(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager)
    whenever(connectivityManager.activeNetwork).thenReturn(null)
  }

  @Test
  fun initialOfflineStateUpdatesFromCapabilitiesAndLostCallback() {
    setContent()
    composeTestRule.onNodeWithText("Offline").assertTextEquals("Offline")
    val callback = registeredCallback()

    val onlineCapabilities = capabilities(hasInternet = true, isValidated = true)
    composeTestRule.runOnIdle { callback.onCapabilitiesChanged(network, onlineCapabilities) }
    composeTestRule.onNodeWithText("Online").assertTextEquals("Online")

    // Network callbacks must use their supplied state and never query stale manager state.
    org.mockito.Mockito.clearInvocations(connectivityManager)
    composeTestRule.runOnIdle { callback.onLost(network) }
    composeTestRule.onNodeWithText("Offline").assertTextEquals("Offline")
    verify(connectivityManager, never()).getNetworkCapabilities(network)
    verifyNoMoreInteractions(connectivityManager)
  }

  @Test
  fun initialOnlineStateRequiresValidatedInternetAndUnregistersCallback() {
    val activeNetwork: Network = mock()
    val initialCapabilities = capabilities(hasInternet = true, isValidated = true)
    val showObserver = mutableStateOf(true)
    whenever(connectivityManager.activeNetwork).thenReturn(activeNetwork)
    whenever(connectivityManager.getNetworkCapabilities(activeNetwork))
        .thenReturn(initialCapabilities)
    composeTestRule.setContent {
      CompositionLocalProvider(LocalContext provides context) {
        if (showObserver.value) {
          Text(if (rememberInternetConnectivity()) "Online" else "Offline")
        }
      }
    }
    composeTestRule.onNodeWithText("Online").assertTextEquals("Online")
    val callback = registeredCallback()

    composeTestRule.runOnIdle {
      callback.onCapabilitiesChanged(
          network,
          capabilities(hasInternet = true, isValidated = false),
      )
    }
    composeTestRule.onNodeWithText("Offline").assertTextEquals("Offline")

    composeTestRule.runOnIdle { showObserver.value = false }
    composeTestRule.waitForIdle()
    verify(connectivityManager).unregisterNetworkCallback(callback)
  }

  @Test
  fun initialNetworkWithoutInternetIsOffline() {
    val activeNetwork: Network = mock()
    val initialCapabilities = capabilities(hasInternet = false, isValidated = true)
    whenever(connectivityManager.activeNetwork).thenReturn(activeNetwork)
    whenever(connectivityManager.getNetworkCapabilities(activeNetwork))
        .thenReturn(initialCapabilities)

    setContent()

    composeTestRule.onNodeWithText("Offline").assertTextEquals("Offline")
    registeredCallback()
  }

  private fun setContent() {
    composeTestRule.setContent {
      CompositionLocalProvider(LocalContext provides context) {
        Text(if (rememberInternetConnectivity()) "Online" else "Offline")
      }
    }
  }

  private fun registeredCallback(): ConnectivityManager.NetworkCallback {
    val callbackCaptor = argumentCaptor<ConnectivityManager.NetworkCallback>()
    verify(connectivityManager).registerDefaultNetworkCallback(callbackCaptor.capture())
    return callbackCaptor.firstValue
  }

  private fun capabilities(hasInternet: Boolean, isValidated: Boolean): NetworkCapabilities =
      mock<NetworkCapabilities>().also {
        whenever(it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            .thenReturn(hasInternet)
        whenever(it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
            .thenReturn(isValidated)
      }
}
