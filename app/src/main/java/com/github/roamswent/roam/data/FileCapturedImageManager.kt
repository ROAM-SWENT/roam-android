package com.github.roamswent.roam.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.github.roamswent.roam.repository.CapturedImageManager
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class FileCapturedImageManager(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher,
    private val uriFactory: (Context, File) -> Uri = { ctx, file ->
      FileProvider.getUriForFile(
          ctx,
          "${ctx.packageName}.fileprovider",
          file,
      )
    },
) : CapturedImageManager {
  override suspend fun newCaptureUri(): Uri =
      withContext(ioDispatcher) {
        val directory = File(context.cacheDir, CAPTURES_DIRECTORY)
        val file = File(directory, CAPTURE_FILE_NAME)
        file.delete()
        directory.mkdirs()
        file.createNewFile()
        uriFactory(context, file)
      }

  override suspend fun deleteCapture(uri: Uri): Boolean =
      withContext(ioDispatcher) {
        val path =
            uri.path?.replace(File.separatorChar, '/')?.let {
              if (!it.startsWith("/")) "/$it" else it
            }
        if (
            uri.scheme != "content" ||
                uri.authority != "${context.packageName}$FILE_PROVIDER_SUFFIX" ||
                path != CAPTURE_PATH
        ) {
          return@withContext false
        }
        File(context.cacheDir, "$CAPTURES_DIRECTORY/$CAPTURE_FILE_NAME").delete()
      }

  private companion object {
    const val CAPTURES_DIRECTORY = "captures"
    const val CAPTURE_FILE_NAME = "capture.jpg"
    const val CAPTURE_PATH = "/$CAPTURES_DIRECTORY/$CAPTURE_FILE_NAME"
    const val FILE_PROVIDER_SUFFIX = ".fileprovider"
  }
}
