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
        directory.mkdirs()
        directory
            .listFiles()
            ?.filter { it.isFile && it.name.matches(Regex("""capture_[A-Za-z0-9_-]+\.jpg""")) }
            ?.forEach { it.delete() }
        val file =
            File.createTempFile(
                "capture_",
                ".jpg",
                directory,
            )

        try {
          uriFactory(context, file)
        } catch (e: Exception) {
          file.delete()
          throw e
        }
      }

  override suspend fun deleteCapture(uri: Uri): Boolean =
      withContext(ioDispatcher) {
        if (
            uri.scheme != "content" ||
                uri.authority != "${context.packageName}$FILE_PROVIDER_SUFFIX"
        ) {
          return@withContext false
        }

        val path =
            uri.path?.replace(File.separatorChar, '/')?.let {
              if (!it.startsWith("/")) "/$it" else it
            } ?: return@withContext false

        val filename = path.substringAfterLast('/')

        if (
            path != "/$CAPTURES_DIRECTORY/$filename" ||
                !filename.matches(Regex("""capture_[A-Za-z0-9_-]+\.jpg"""))
        ) {
          return@withContext false
        }

        val file =
            File(
                File(context.cacheDir, CAPTURES_DIRECTORY),
                filename,
            )

        file.delete()
      }

  private companion object {
    const val CAPTURES_DIRECTORY = "captures"
    const val FILE_PROVIDER_SUFFIX = ".fileprovider"
  }
}
