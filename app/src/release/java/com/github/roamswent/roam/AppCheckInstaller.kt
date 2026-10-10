package com.github.roamswent.roam

/**
 * Release builds keep the default App Check provider, which attests with Play Integrity. Nothing is
 * installed here, so the debug provider never ships.
 */
internal object AppCheckInstaller {

  fun install() {}
}
