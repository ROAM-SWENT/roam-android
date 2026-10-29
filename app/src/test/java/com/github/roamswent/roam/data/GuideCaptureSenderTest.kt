/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.data

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.github.roamswent.roam.model.ai.DiscoveryRequest
import com.github.roamswent.roam.model.ai.MonumentGuide
import com.github.roamswent.roam.model.ai.MonumentGuideRepository
import com.github.roamswent.roam.model.ai.MonumentNotIdentifiedException
import com.github.roamswent.roam.model.interests.UserInterestsRepository
import com.github.roamswent.roam.model.interests.UserInterestsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GuideCaptureSenderTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val uri = Uri.parse("content://example/capture.jpg")
  private val bytes = byteArrayOf(1, 2, 3)
  private val guide =
      MonumentGuide(
          monumentName = "Eiffel Tower",
          confidence = 0.95,
          history = "Built in 1889.",
          facts = listOf("330 meters tall"),
          culture = "Symbol of Paris.",
          language = "en",
      )

  @Test
  fun sendReadsBytesAndReturnsTheGuide() = runTest {
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
