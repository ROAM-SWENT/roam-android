// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.ui.loading

import android.content.ContentResolver
import android.net.Uri
import com.github.roamswent.roam.model.ai.ImagePayload
import com.github.roamswent.roam.model.ai.ImageValidator
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class ContentResolverImageReader(
    private val contentResolver: ContentResolver,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  suspend fun read(uri: Uri): ImagePayload =
      withContext(dispatcher) {
          val stream =
            contentResolver.openInputStream(uri)
                ?: throw IOException("Could not load the photo. Please try again.")
          val bytes = stream.use { it.readBytes() }
          val mimeType =
              contentResolver.getType(uri)
                  ?: ImageValidator.detectFormat(bytes)?.mimeType
                  ?: throw IOException("Unsupported image format")
          ImagePayload(bytes, mimeType)
      }
}
