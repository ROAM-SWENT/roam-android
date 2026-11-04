// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.ui.loading

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import com.github.roamswent.roam.model.ai.ImagePreparer
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContentResolverImageReaderTest {
  private val contentResolver = mockk<ContentResolver>()
  private val uri = Uri.parse("content://capture/photo.jpg")

  @Test
  fun readsImageBytesFromContentResolver() = runTest {
    val expectedBytes = byteArrayOf(1, 2, 3)
    every { contentResolver.openInputStream(uri) } returns ByteArrayInputStream(expectedBytes)
    every { contentResolver.getType(uri) } returns "image/jpeg"

    val payload = ContentResolverImageReader(contentResolver).read(uri)

    assertArrayEquals(expectedBytes, payload.bytes)
    assertEquals("image/jpeg", payload.mimeType)
  }

  @Test
  fun usesProviderMimeTypeForGalleryImages() = runTest {
    val galleryUri = Uri.parse("content://gallery/image.png")
    val expectedBytes = byteArrayOf(4, 5, 6)
    every { contentResolver.openInputStream(galleryUri) } returns
        ByteArrayInputStream(expectedBytes)
    every { contentResolver.getType(galleryUri) } returns "image/png"

    val payload = ContentResolverImageReader(contentResolver).read(galleryUri)

    assertArrayEquals(expectedBytes, payload.bytes)
    assertEquals("image/png", payload.mimeType)
  }

  @Test
  fun readsAndPreparesPngSelectedFromGallery() = runTest {
    val galleryUri = Uri.parse("content://gallery/image.png")
    val pngBytes = pngImageBytes()
    every { contentResolver.openInputStream(galleryUri) } returns ByteArrayInputStream(pngBytes)
    every { contentResolver.getType(galleryUri) } returns null

    val rawPayload = ContentResolverImageReader(contentResolver).read(galleryUri)
    val preparedPayload = ImagePreparer().prepare(rawPayload.bytes, rawPayload.mimeType)

    assertEquals("image/png", rawPayload.mimeType)
    assertArrayEquals(pngBytes, rawPayload.bytes)
    assertEquals("image/jpeg", preparedPayload.mimeType)
    assertTrue(preparedPayload.bytes.isNotEmpty())
  }

  @Test
  fun nullStreamThrowsUserFacingError() = runTest {
    every { contentResolver.openInputStream(uri) } returns null
    every { contentResolver.getType(uri) } returns "image/jpeg"

    val exception =
        try {
          ContentResolverImageReader(contentResolver).read(uri)
          null
        } catch (exception: IOException) {
          exception
        }

    assertNotNull(exception)
    assertEquals(
        "Could not load the photo. Please try again.",
        exception?.message,
    )
  }

  private fun pngImageBytes(): ByteArray {
    val bitmap = Bitmap.createBitmap(128, 96, Bitmap.Config.ARGB_8888)
    val output = ByteArrayOutputStream()
    try {
      bitmap.eraseColor(Color.CYAN)
      check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
      return output.toByteArray()
    } finally {
      bitmap.recycle()
    }
  }
}
