package com.github.roamswent.roam.repository

import android.net.Uri

interface CaptureSender {
  suspend fun send(uri: Uri): Result<Unit>
}
