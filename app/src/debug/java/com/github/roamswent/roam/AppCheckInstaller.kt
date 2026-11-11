/*
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
 */

package com.github.roamswent.roam

import android.os.Build
import com.google.firebase.Firebase
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Debug builds pick the App Check provider at runtime:
 * - On an emulator, the debug provider. Emulators cannot pass Play Integrity, so the debug provider
 *   sends a debug token instead. That token must be registered in the Firebase console (see the
 *   README, "Firebase App Check in debug builds").
 * - On a real device, Play Integrity, so debug APKs shared with testers work without registering a
 *   token per install.
 */
internal object AppCheckInstaller {

  fun install() {
    Firebase.appCheck.installAppCheckProviderFactory(providerFactory(DeviceInfo.current()))
  }

  internal fun providerFactory(device: DeviceInfo): AppCheckProviderFactory =
      when (selectProvider(device)) {
        AppCheckProvider.DEBUG -> DebugAppCheckProviderFactory.getInstance()
        AppCheckProvider.PLAY_INTEGRITY -> PlayIntegrityAppCheckProviderFactory.getInstance()
      }

  internal fun selectProvider(device: DeviceInfo): AppCheckProvider =
      if (EmulatorDetector.isEmulator(device)) AppCheckProvider.DEBUG
      else AppCheckProvider.PLAY_INTEGRITY
}

internal enum class AppCheckProvider {
  DEBUG,
  PLAY_INTEGRITY,
}

/** The subset of [Build] fields used to recognise an emulator, so the logic is testable. */
internal data class DeviceInfo(
    val fingerprint: String,
    val product: String,
    val model: String,
    val hardware: String,
    val manufacturer: String,
    val brand: String,
    val device: String,
) {
  companion object {
    fun current(): DeviceInfo =
        DeviceInfo(
            fingerprint = Build.FINGERPRINT.orEmpty(),
            product = Build.PRODUCT.orEmpty(),
            model = Build.MODEL.orEmpty(),
            hardware = Build.HARDWARE.orEmpty(),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            device = Build.DEVICE.orEmpty(),
        )
  }
}

/** Recognises the Android Studio emulator (goldfish/ranchu) and common third-party emulators. */
internal object EmulatorDetector {

  private val emulatorHardware = setOf("goldfish", "ranchu", "vbox86")
  private val emulatorProductMarkers =
      listOf(
          "sdk_gphone",
          "google_sdk",
          "sdk_x86",
          "sdk_google",
          "vbox86p",
          "emulator",
          "simulator",
      )

  fun isEmulator(device: DeviceInfo): Boolean =
      device.hardware.lowercase() in emulatorHardware ||
          emulatorProductMarkers.any { device.product.lowercase().contains(it) } ||
          device.product.lowercase() == "sdk" ||
          device.fingerprint.startsWith("generic") ||
          device.fingerprint.startsWith("unknown") ||
          device.fingerprint.contains("/sdk_gphone") ||
          device.model.contains("google_sdk") ||
          device.model.contains("Emulator") ||
          device.model.contains("Android SDK built for") ||
          device.manufacturer.contains("Genymotion") ||
          (device.brand.startsWith("generic") && device.device.startsWith("generic"))
}
