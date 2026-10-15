package com.github.roamswent.roam.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FileCapturedImageManagerTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val capturesDirectory = File(context.cacheDir, "captures")

  private fun captureFile(uri: Uri): File =
      File(capturesDirectory, requireNotNull(uri.lastPathSegment))

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

    assertEquals("${context.packageName}.fileprovider", uri.authority)
    assertTrue(uri.path?.startsWith("/captures/capture_") == true)
    assertTrue(uri.path?.endsWith(".jpg") == true)
    assertTrue(captureFile(uri).exists())
  }

  @Test
  fun newCaptureUriDeletesPreviousCapture() = runTest {
    val manager = manager()

    val firstUri = manager.newCaptureUri()
    val firstFile = captureFile(firstUri)

    assertTrue(firstFile.exists())

    val secondUri = manager.newCaptureUri()
    val secondFile = captureFile(secondUri)

    assertNotEquals(firstUri, secondUri)
    assertFalse(firstFile.exists())
    assertTrue(secondFile.exists())
    assertEquals(1, capturesDirectory.listFiles()?.size)
  }

  @Test
  fun deleteCaptureDeletesManagerProducedFile() = runTest {
    val uri = manager().newCaptureUri()
    val file = captureFile(uri)

    assertTrue(file.exists())
    assertTrue(manager().deleteCapture(uri))
    assertFalse(file.exists())
  }

  @Test
  fun newCaptureUriDeletesOnlyMatchingCaptureFiles() = runTest {
    capturesDirectory.mkdirs()

    val oldCapture = File(capturesDirectory, "capture_old.jpg").apply { writeText("old") }

    val unrelatedFile = File(capturesDirectory, "important.jpg").apply { writeText("keep") }

    val wrongExtension = File(capturesDirectory, "capture_test.txt").apply { writeText("keep") }

    val newUri = manager().newCaptureUri()

    assertFalse(oldCapture.exists())
    assertTrue(unrelatedFile.exists())
    assertTrue(wrongExtension.exists())
    assertTrue(captureFile(newUri).exists())
  }

  @Test
  fun deleteCaptureRejectsUriWithForeignAuthority() = runTest {
    val uri = manager().newCaptureUri()
    val foreignUri = Uri.parse("content://foreign.example/other.jpg")

    assertFalse(manager().deleteCapture(foreignUri))
    assertTrue(captureFile(uri).exists())
  }

  @Test
  fun deleteCaptureRejectsForeignAuthorityUri() = runTest {
    val target = manager().newCaptureUri()
    val foreignUri = target.buildUpon().authority("foreign.example").build()

    assertFalse(manager().deleteCapture(foreignUri))
    assertTrue(captureFile(target).exists())
  }

  @Test
  fun deleteCaptureRejectsCorrectAuthorityWithWrongPath() = runTest {
    val target = manager().newCaptureUri()
    val file = captureFile(target)
    file.writeText("capture image")

    val wrongPathUri = target.buildUpon().path("/captures/other.jpg").build()

    assertFalse(manager().deleteCapture(wrongPathUri))
    assertTrue(file.exists())
    assertEquals("capture image", file.readText())
  }

  @Test
  fun deleteCaptureRejectsCorrectAuthorityWithUnrelatedPathEndingInFilename() = runTest {
    val target = manager().newCaptureUri()
    val file = captureFile(target)
    file.writeText("capture image")

    val unrelatedUri = target.buildUpon().path("/unrelated/${file.name}").build()

    assertFalse(manager().deleteCapture(unrelatedUri))
    assertTrue(file.exists())
    assertEquals("capture image", file.readText())
  }

  @Test
  fun deleteCaptureRejectsNonContentScheme() = runTest {
    val target = manager().newCaptureUri()
    val file = captureFile(target)
    file.writeText("capture image")

    val fileSchemeUri = target.buildUpon().scheme("file").build()

    assertFalse(manager().deleteCapture(fileSchemeUri))
    assertTrue(file.exists())
  }

  @Test
  fun deleteCaptureRejectsCorrectAuthorityWithNullPath() = runTest {
    val target = manager().newCaptureUri()
    val file = captureFile(target)
    file.writeText("capture image")

    val nullPathUri = target.buildUpon().path(null).build()

    assertFalse(manager().deleteCapture(nullPathUri))
    assertTrue(file.exists())
    assertEquals("capture image", file.readText())
  }

  @Test
  fun deleteCaptureReturnsFalseWhenFileWasAlreadyRemoved() = runTest {
    val uri = manager().newCaptureUri()
    assertTrue(captureFile(uri).delete())

    assertFalse(manager().deleteCapture(uri))
  }

  @Test
  fun newCaptureUriRunsOnInjectedDispatcher() = runTest {
    val dispatcher = StandardTestDispatcher(testScheduler)
    val result = async { manager(dispatcher).newCaptureUri() }

    assertFalse(result.isCompleted)
    advanceUntilIdle()

    val uri = result.await()
    assertTrue(uri.path?.contains("/captures/capture_") == true)
    assertTrue(captureFile(uri).exists())
  }

  @Test
  fun defaultUriFactoryDelegatesToFileProvider() = runTest {
    val authority = "${context.packageName}.fileprovider"

    Mockito.mockStatic(FileProvider::class.java).use { mocked ->
      mocked
          .`when`<Uri> {
            FileProvider.getUriForFile(
                ArgumentMatchers.eq(context),
                ArgumentMatchers.eq(authority),
                ArgumentMatchers.any(File::class.java),
            )
          }
          .thenAnswer { invocation ->
            val file = invocation.getArgument<File>(2)
            Uri.parse("content://$authority/captures/${file.name}")
          }

      val manager = FileCapturedImageManager(context, UnconfinedTestDispatcher())

      val result = manager.newCaptureUri()
      val generatedFile = captureFile(result)

      assertTrue(generatedFile.exists())
      assertTrue(generatedFile.name.startsWith("capture_"))

      mocked.verify {
        FileProvider.getUriForFile(
            context,
            authority,
            generatedFile,
        )
      }
    }
  }

  @Test
  fun concurrentCapturesCreateUniqueFiles() = runTest {
    assertFalse(capturesDirectory.exists())

    val manager = manager(Dispatchers.IO)
    val readyCount = AtomicInteger(0)
    val start = CompletableDeferred<Unit>()

    val captures =
        List(2) {
          async(Dispatchers.IO) {
            if (readyCount.incrementAndGet() == 2) {
              start.complete(Unit)
            }

            start.await()
            manager.newCaptureUri()
          }
        }

    val uris = captures.awaitAll()

    assertNotEquals(uris[0], uris[1])

    val survivingFiles = capturesDirectory.listFiles().orEmpty()

    assertTrue(survivingFiles.size in 1..2)
    assertTrue(uris.any { captureFile(it).exists() })
    assertTrue(survivingFiles.all { file -> uris.any { captureFile(it) == file } })
  }

  @Test
  fun deleteCaptureWorksWithDefaultFileProvider() = runTest {
    // FileProvider path resolution is incompatible with
    // Windows filesystem separators under Robolectric.
    assumeTrue(File.separatorChar == '/')

    val manager =
        FileCapturedImageManager(
            context,
            UnconfinedTestDispatcher(testScheduler),
        )

    val uri = manager.newCaptureUri()
    val file = capturesDirectory.listFiles()?.singleOrNull()

    assertNotNull(file)
    assertTrue(manager.deleteCapture(uri))
    assertFalse(file!!.exists())
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
