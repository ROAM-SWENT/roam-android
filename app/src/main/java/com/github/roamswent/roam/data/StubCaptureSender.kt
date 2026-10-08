package com.github.roamswent.roam.data

import android.net.Uri
import com.github.roamswent.roam.repository.CaptureSender

class StubCaptureSender : CaptureSender {
  override suspend fun send(uri: Uri): Result<Unit> = Result.success(Unit)
}
