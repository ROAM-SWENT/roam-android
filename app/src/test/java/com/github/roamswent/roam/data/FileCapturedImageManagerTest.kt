package com.github.roamswent.roam.data

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FileCapturedImageManagerTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val capturesDirectory = File(context.cacheDir, "captures")
  private val captureFile = File(capturesDirectory, "capture.jpg")

  @Before
  fun setUp() {
    clearFileProviderCache()
    deleteRecursively(capturesDirectory)
  }

  @After
  fun tearDown() {
    deleteRecursively(capturesDirectory)
  }

  @Test
  fun newCaptureUriPointsToExistingFileWithExpectedAuthority() = runTest {
    val uri = manager().newCaptureUri()

    assertEquals("${context.packageName}.fileprovider", uri.authority)
    assertTrue(uri.path?.endsWith("captures/capture.jpg") == true)
    assertTrue(captureFile.exists())
  }

  @Test
  fun newCaptureUriReplacesTheExistingSingleImage() = runTest {
    val firstUri = manager().newCaptureUri()
    captureFile.writeText("old image")

    val secondUri = manager().newCaptureUri()

    assertEquals(firstUri.path, secondUri.path)
    assertEquals(listOf(captureFile.name), capturesDirectory.list()?.toList())
    assertTrue(captureFile.exists())
    assertEquals(0L, captureFile.length())
  }

  @Test
  fun deleteCaptureDeletesManagerProducedFile() = runTest {
    val uri = manager().newCaptureUri()

    assertTrue(manager().deleteCapture(uri))
    assertFalse(captureFile.exists())
  }

  @Test
  fun deleteCaptureRejectsUriWithForeignAuthority() = runTest {
    manager().newCaptureUri()
    val foreignUri = android.net.Uri.parse("content://foreign.example/other.jpg")

    assertFalse(manager().deleteCapture(foreignUri))
    assertTrue(captureFile.exists())
  }

  @Test
  fun deleteCaptureRejectsForeignAuthorityUri() = runTest {
    val target = manager().newCaptureUri()
    val foreignAuthorityUri = target.buildUpon().authority("foreign.example").build()

    assertFalse(manager().deleteCapture(foreignAuthorityUri))
    assertTrue(captureFile.exists())
  }

  @Test
  fun deleteCaptureRejectsCorrectAuthorityWithWrongPathSuffix() = runTest {
    val target = manager().newCaptureUri()
    captureFile.writeText("capture image")
    val wrongPathUri = target.buildUpon().path("/captures/other.jpg").build()

    assertFalse(manager().deleteCapture(wrongPathUri))
    assertTrue(captureFile.exists())
    assertEquals("capture image", captureFile.readText())
  }

  @Test
  fun deleteCaptureRejectsCorrectAuthorityWithNullPath() = runTest {
    val target = manager().newCaptureUri()
    captureFile.writeText("capture image")
    val nullPathUri = target.buildUpon().path(null).build()

    assertFalse(manager().deleteCapture(nullPathUri))
    assertTrue(captureFile.exists())
    assertEquals("capture image", captureFile.readText())
  }

  @Test
  fun deleteCaptureReturnsFalseWhenFileWasAlreadyRemoved() = runTest {
    val uri = manager().newCaptureUri()
    assertTrue(captureFile.delete())

    assertFalse(manager().deleteCapture(uri))
  }

  @Test
  fun newCaptureUriRunsOnInjectedDispatcher() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val result = async { FileCapturedImageManager(context, dispatcher).newCaptureUri() }

    assertFalse(result.isCompleted)
    advanceUntilIdle()

    assertTrue(result.await().path?.endsWith("capture.jpg") == true)
  }

  private fun manager() = FileCapturedImageManager(context, UnconfinedTestDispatcher())

  @Suppress("UNCHECKED_CAST")
  private fun clearFileProviderCache() {
    val cacheField = FileProvider::class.java.getDeclaredField("sCache")
    cacheField.isAccessible = true
    (cacheField.get(null) as MutableMap<Any, Any>).clear()
  }

  private fun deleteRecursively(file: File) {
    if (file.isDirectory) {
      file.listFiles()?.forEach(::deleteRecursively)
    }
    file.delete()
  }
}
