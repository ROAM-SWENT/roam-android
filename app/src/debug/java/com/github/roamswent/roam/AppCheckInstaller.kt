package com.github.roamswent.roam

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds use the App Check debug provider. It accepts a registered debug token instead of a
 * Play Integrity attestation, which emulators cannot produce.
 */
internal object AppCheckInstaller {

  fun install() {
    Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
  }
}
