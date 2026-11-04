/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.github.roamswent.roam.model.ai.DiscoveryRequest
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.ai.MonumentGuideRepository
import com.github.roamswent.roam.model.ai.MonumentNotIdentifiedException
import com.github.roamswent.roam.model.interests.UserInterestsRepository
import com.github.roamswent.roam.model.interests.UserInterestsState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GuideCaptureSenderTest {
  private val context = mockk<Context>()
  private val contentResolver = mockk<ContentResolver>()
  private val uri = Uri.parse("content://example/capture.jpg")
  private val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
  private val guide =
      MonumentGuide(
          monumentName = "Eiffel Tower",
          confidence = 0.95,
          history = "Built in 1889.",
          facts = listOf("330 meters tall"),
          culture = "Symbol of Paris.",
          language = "en",
      )

  @Before
  fun setUp() {
    every { context.contentResolver } returns contentResolver
    every { contentResolver.getType(uri) } returns null
  }

  @Test
  fun sendReadsBytesAndReturnsTheGuide() = runTest {
    every { contentResolver.getType(uri) } returns "image/jpeg"
    val repository = FakeMonumentGuideRepository { guide }
    val sender =
        GuideCaptureSender(
            context,
            repository,
            FakeUserInterestsRepository(),
            readBytes = { bytes },
        )

    val result = sender.send(uri)

    assertEquals(Result.success(guide), result)
    val request = requireNotNull(repository.lastRequest)
    assertEquals("image/jpeg", request.image.mimeType)
    assertTrue(request.image.bytes.contentEquals(bytes))
    assertNull(request.location)
    assertTrue(request.preferences.interests.isEmpty())
  }

  @Test
  fun sendForwardsPngMimeTypeFromContentResolver() = runTest {
    val pngUri = Uri.parse("content://example/image.png")
    every { contentResolver.getType(pngUri) } returns "image/png"
    val repository = FakeMonumentGuideRepository { guide }
    val sender =
        GuideCaptureSender(
            context,
            repository,
            FakeUserInterestsRepository(),
            readBytes = { bytes },
        )

    val result = sender.send(pngUri)

    assertEquals(Result.success(guide), result)
    assertEquals("image/png", requireNotNull(repository.lastRequest).image.mimeType)
  }

  @Test
  fun sendForwardsJpegMimeTypeFromContentResolver() = runTest {
    every { contentResolver.getType(uri) } returns "image/jpeg"
    val repository = FakeMonumentGuideRepository { guide }
    val sender =
        GuideCaptureSender(
            context,
            repository,
            FakeUserInterestsRepository(),
            readBytes = { bytes },
        )

    sender.send(uri)

    assertEquals("image/jpeg", requireNotNull(repository.lastRequest).image.mimeType)
  }

  @Test
  fun sendFallsBackToJpegWhenMimeTypeIsUnavailable() = runTest {
    every { contentResolver.getType(uri) } returns null
    val repository = FakeMonumentGuideRepository { guide }
    val sender =
        GuideCaptureSender(
            context,
            repository,
            FakeUserInterestsRepository(),
            readBytes = { bytes },
        )

    sender.send(uri)

    assertEquals("image/jpeg", requireNotNull(repository.lastRequest).image.mimeType)
  }

  @Test
  fun sendDetectsPngMimeTypeWhenContentResolverTypeIsUnavailable() = runTest {
    val pngUri = Uri.parse("content://example/image.png")
    val pngBytes =
        byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
        ) + ByteArray(16)
    every { contentResolver.getType(pngUri) } returns null
    val repository = FakeMonumentGuideRepository { guide }
    val sender =
        GuideCaptureSender(
            context,
            repository,
            FakeUserInterestsRepository(),
            readBytes = { pngBytes },
        )

    sender.send(pngUri)

    val image = requireNotNull(repository.lastRequest).image
    assertEquals("image/png", image.mimeType)
    assertTrue(image.bytes.contentEquals(pngBytes))
  }

  @Test
  fun sendIncludesRepositoryInterestsInTheDiscoveryRequest() = runTest {
    val interestsRepository = FakeUserInterestsRepository(listOf("art", "architecture"))
    val repository = FakeMonumentGuideRepository { guide }
    val sender =
        GuideCaptureSender(
            context,
            repository,
            userInterestsRepository = interestsRepository,
            readBytes = { bytes },
        )

    sender.send(uri)

    assertEquals(
        listOf("art", "architecture"),
        requireNotNull(repository.lastRequest).preferences.interests,
    )
  }

  @Test
  fun sendWrapsAThrownDiscoveryExceptionAsFailure() = runTest {
    val repository = FakeMonumentGuideRepository { throw MonumentNotIdentifiedException() }
    val sender =
        GuideCaptureSender(
            context,
            repository,
            FakeUserInterestsRepository(),
            readBytes = { bytes },
        )

    val result = sender.send(uri)

    assertTrue(result.isFailure)
    assertTrue(result.exceptionOrNull() is MonumentNotIdentifiedException)
  }

  @Test
  fun sendRethrowsCancellationExceptionInsteadOfWrappingIt() = runTest {
    val repository = FakeMonumentGuideRepository { throw CancellationException("cancelled") }
    val sender =
        GuideCaptureSender(
            context,
            repository,
            FakeUserInterestsRepository(),
            readBytes = { bytes },
        )

    var caught: CancellationException? = null
    try {
      sender.send(uri)
    } catch (e: CancellationException) {
      caught = e
    }

    assertNotNull(caught)
  }

  private class FakeUserInterestsRepository(private var interests: List<String> = emptyList()) :
      UserInterestsRepository {
    override fun getUserInterests() = UserInterestsState(hasBeenAsked = true, interests = interests)

    override fun updateInterests(interests: List<String>) {
      this.interests = interests
    }

    override fun saveInterests(interests: List<String>) {
      this.interests = interests
    }

    override fun skipInterests() {
      interests = emptyList()
    }
  }

  private class FakeMonumentGuideRepository(private val result: () -> MonumentGuide) :
      MonumentGuideRepository {
    var lastRequest: DiscoveryRequest? = null

    override suspend fun guideFor(request: DiscoveryRequest): MonumentGuide {
      lastRequest = request
      return result()
    }
  }
}
