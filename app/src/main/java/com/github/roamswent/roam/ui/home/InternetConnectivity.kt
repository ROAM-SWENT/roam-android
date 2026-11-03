/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.home

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun rememberInternetConnectivity(): Boolean {
  val context = LocalContext.current
  val connectivityManager =
      remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
      }
  var isConnected by
      remember(connectivityManager) {
        mutableStateOf(connectivityManager.hasValidatedInternetConnection())
      }

  DisposableEffect(connectivityManager) {
    val callback =
        object : ConnectivityManager.NetworkCallback() {
          override fun onCapabilitiesChanged(
              network: Network,
              networkCapabilities: NetworkCapabilities,
          ) {
            isConnected = networkCapabilities.hasValidatedInternet()
          }

          override fun onLost(network: Network) {
            isConnected = false
          }
        }

    connectivityManager.registerDefaultNetworkCallback(callback)
    onDispose { connectivityManager.unregisterNetworkCallback(callback) }
  }

  return isConnected
}

private fun ConnectivityManager.hasValidatedInternetConnection(): Boolean =
    getNetworkCapabilities(activeNetwork)?.hasValidatedInternet() == true

// A validated network confirms Android has verified actual internet access.
private fun NetworkCapabilities.hasValidatedInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
