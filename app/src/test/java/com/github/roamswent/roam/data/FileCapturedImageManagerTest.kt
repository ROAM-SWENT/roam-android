package com.github.roamswent.roam.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
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
    deleteRecursively(capturesDirectory)
  }

  @After
  fun tearDown() {
    deleteRecursively(capturesDirectory)
  }

  @Test
  fun newCaptureUriPointsToExistingFileWithExpectedAuthority() = runTest {
    val uri = manager().newCaptureUri()
    val path =
        uri.path?.replace(File.separatorChar, '/')?.let { if (!it.startsWith("/")) "/$it" else it }

    assertEquals("${context.packageName}.fileprovider", uri.authority)
    assertTrue(path?.endsWith("captures/capture.jpg") == true)
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
  fun deleteCaptureRejectsCorrectAuthorityWithWrongPath() = runTest {
    val target = manager().newCaptureUri()
    captureFile.writeText("capture image")
    val wrongPathUri = target.buildUpon().path("/captures/other.jpg").build()

    assertFalse(manager().deleteCapture(wrongPathUri))
    assertTrue(captureFile.exists())
    assertEquals("capture image", captureFile.readText())
  }

  @Test
  fun deleteCaptureRejectsCorrectAuthorityWithUnrelatedPathEndingInFilename() = runTest {
    val target = manager().newCaptureUri()
    captureFile.writeText("capture image")
    val unrelatedPathUri = target.buildUpon().path("/unrelated/capture.jpg").build()

    assertFalse(manager().deleteCapture(unrelatedPathUri))
    assertTrue(captureFile.exists())
    assertEquals("capture image", captureFile.readText())
  }

  @Test
  fun deleteCaptureRejectsNonContentScheme() = runTest {
    val target = manager().newCaptureUri()
    captureFile.writeText("capture image")
    val fileSchemeUri = target.buildUpon().scheme("file").build()

    assertFalse(manager().deleteCapture(fileSchemeUri))
    assertTrue(captureFile.exists())
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
    val result = async { manager(dispatcher).newCaptureUri() }

    assertFalse(result.isCompleted)
    advanceUntilIdle()

    assertTrue(result.await().path?.endsWith("capture.jpg") == true)
  }

  private fun manager(dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()) =
      FileCapturedImageManager(context, dispatcher) { ctx, file ->
        Uri.Builder()
            .scheme("content")
            .authority("${ctx.packageName}.fileprovider")
            .appendPath("captures")
            .appendPath(file.name)
            .build()
      }

  private fun deleteRecursively(file: File) {
    if (file.isDirectory) {
      file.listFiles()?.forEach(::deleteRecursively)
    }
    file.delete()
  }
}
