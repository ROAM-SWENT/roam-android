package com.github.roamswent.roam

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.initialize

class RoamApplication : Application() {

  override fun onCreate() {
    super.onCreate()
    Firebase.initialize(this)
    AppCheckInstaller.install()
  }
}
