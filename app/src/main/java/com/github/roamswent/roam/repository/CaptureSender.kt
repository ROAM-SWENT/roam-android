package com.github.roamswent.roam.repository

import android.net.Uri
import com.github.roamswent.roam.model.ai.MonumentGuide

interface CaptureSender {
  suspend fun send(uri: Uri): Result<MonumentGuide>
}
