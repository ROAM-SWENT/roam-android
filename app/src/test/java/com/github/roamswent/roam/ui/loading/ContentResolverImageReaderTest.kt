// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>

package com.github.roamswent.roam.ui.loading

import android.content.ContentResolver
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContentResolverImageReaderTest {
  private val contentResolver = mockk<ContentResolver>()
  private val uri = Uri.parse("content://capture/photo.jpg")

  @Test
  fun readsImageBytesFromContentResolver() = runTest {
    val expectedBytes = byteArrayOf(1, 2, 3)
    every { contentResolver.openInputStream(uri) } returns ByteArrayInputStream(expectedBytes)

    val payload = ContentResolverImageReader(contentResolver).read(uri)

    assertArrayEquals(expectedBytes, payload.bytes)
    assertEquals("image/jpeg", payload.mimeType)
  }

  @Test
  fun nullStreamThrowsUserFacingError() = runTest {
    every { contentResolver.openInputStream(uri) } returns null

    val exception =
        try {
          ContentResolverImageReader(contentResolver).read(uri)
          null
        } catch (exception: IOException) {
          exception
        }

    assertNotNull(exception)
    assertEquals(
        "Could not load the captured photo. Please try again.",
        exception?.message,
    )
  }
}
