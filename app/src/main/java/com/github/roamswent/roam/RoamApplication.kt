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
    if (AppCompatDelegate.getApplicationLocales().isEmpty) {
      val defaultTag = if (Locale.getDefault().language == "fr") "fr" else "en"
      AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(defaultTag))
    }
  }
}
