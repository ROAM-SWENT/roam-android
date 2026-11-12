package com.github.roamswent.roam

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.google.firebase.Firebase
import com.google.firebase.initialize
import java.util.Locale

class RoamApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    Firebase.initialize(this)
    AppCheckInstaller.install()
    snapDefaultLocale()
  }

  internal companion object {
    fun snapDefaultLocale(
        currentLocales: LocaleListCompat = AppCompatDelegate.getApplicationLocales(),
        defaultLocale: Locale = Locale.getDefault(),
        setLocales: (LocaleListCompat) -> Unit = AppCompatDelegate::setApplicationLocales,
    ) {
      if (currentLocales.isEmpty) {
        val defaultTag = if (defaultLocale.language == "fr") "fr" else "en"
        setLocales(LocaleListCompat.forLanguageTags(defaultTag))
      }
    }
  }
}
