package com.github.roamswent.roam.repository

import android.net.Uri

interface CapturedImageManager {
  suspend fun newCaptureUri(): Uri

  suspend fun deleteCapture(uri: Uri): Boolean
}
