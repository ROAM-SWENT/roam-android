/*
 * Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
 */

package com.github.roamswent.roam

import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCheckInstallerTest {

  /** Build values read from the Android Studio `Medium_Phone` emulator (API 37). */
  private val androidStudioEmulator =
      DeviceInfo(
          fingerprint =
              "google/sdk_gphone16k_arm64/emu64a16k:17/CE2A.260420.050/16231978:user/dev-keys",
          product = "sdk_gphone16k_arm64",
          model = "sdk_gphone16k_arm64",
          hardware = "ranchu",
          manufacturer = "Google",
          brand = "google",
          device = "emu64a16k",
      )

  private val pixel8 =
      DeviceInfo(
          fingerprint = "google/shiba/shiba:15/AP4A.250105.002/12701944:user/release-keys",
          product = "shiba",
          model = "Pixel 8",
          hardware = "shiba",
          manufacturer = "Google",
          brand = "google",
          device = "shiba",
      )

  private val galaxyS23 =
      DeviceInfo(
          fingerprint = "samsung/dm1qxeea/dm1q:14/UP1A.231005.007/S911BXXS3BWK5:user/release-keys",
          product = "dm1qxeea",
          model = "SM-S911B",
          hardware = "qcom",
          manufacturer = "samsung",
          brand = "samsung",
          device = "dm1q",
      )

  private val noSignal =
      DeviceInfo(
          fingerprint = "",
          product = "",
          model = "",
          hardware = "",
          manufacturer = "",
          brand = "",
          device = "",
      )

  @Test
  fun androidStudioEmulator_isDetected() {
    assertTrue(EmulatorDetector.isEmulator(androidStudioEmulator))
  }

  @Test
  fun realDevices_areNotDetected() {
    assertFalse(EmulatorDetector.isEmulator(pixel8))
    assertFalse(EmulatorDetector.isEmulator(galaxyS23))
    assertFalse(EmulatorDetector.isEmulator(noSignal))
  }

  @Test
  fun eachEmulatorSignal_aloneIsEnough() {
    val signals =
        listOf(
            noSignal.copy(hardware = "goldfish"),
            noSignal.copy(hardware = "RANCHU"),
            noSignal.copy(hardware = "vbox86"),
            noSignal.copy(product = "sdk_gphone64_x86_64"),
            noSignal.copy(product = "google_sdk"),
            noSignal.copy(product = "sdk"),
            noSignal.copy(product = "vbox86p"),
            noSignal.copy(fingerprint = "generic/sdk/generic:8.0.0/OSR1/1:userdebug/test-keys"),
            noSignal.copy(fingerprint = "unknown/unknown/unknown"),
            noSignal.copy(fingerprint = "google/sdk_gphone_x86/generic_x86:11/RSR1/1:user/keys"),
            noSignal.copy(model = "google_sdk"),
            noSignal.copy(model = "Emulator"),
            noSignal.copy(model = "Android SDK built for x86"),
            noSignal.copy(manufacturer = "Genymotion"),
            noSignal.copy(brand = "generic_x86", device = "generic_x86"),
        )
    signals.forEach { assertTrue("expected emulator: $it", EmulatorDetector.isEmulator(it)) }
  }

  @Test
  fun genericBrandWithoutGenericDevice_isNotDetected() {
    assertFalse(EmulatorDetector.isEmulator(noSignal.copy(brand = "generic", device = "shiba")))
  }

  @Test
  fun selectProvider_usesDebugOnEmulator() {
    assertEquals(AppCheckProvider.DEBUG, AppCheckInstaller.selectProvider(androidStudioEmulator))
  }

  @Test
  fun selectProvider_usesPlayIntegrityOnRealDevice() {
    assertEquals(AppCheckProvider.PLAY_INTEGRITY, AppCheckInstaller.selectProvider(pixel8))
    assertEquals(AppCheckProvider.PLAY_INTEGRITY, AppCheckInstaller.selectProvider(galaxyS23))
  }

  @Test
  fun providerFactory_matchesSelectedProvider() {
    assertTrue(
        AppCheckInstaller.providerFactory(androidStudioEmulator) is DebugAppCheckProviderFactory
    )
    assertTrue(AppCheckInstaller.providerFactory(pixel8) is PlayIntegrityAppCheckProviderFactory)
  }

  @Test
  fun currentDeviceInfo_neverHasNullFields() {
    val current = DeviceInfo.current()
    assertNotNull(current.fingerprint)
    assertNotNull(current.product)
  }
}
