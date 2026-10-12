package com.github.roamswent.roam.model.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.APINotConfiguredException
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.InvalidLocationException
import com.google.firebase.ai.type.PermissionMissingException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.SerializationException as FirebaseSerializationException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.UnsupportedUserLocationException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * The only class that talks to Firebase AI Logic. Sends the photo and prompt to Gemini and turns
 * every failure into a [DiscoveryException]. The App Check token is attached by the SDK.
 */
class FirebaseGeminiClient(
    private val context: Context,
    modelName: String = MODEL_NAME,
) : GeminiClient {

  companion object {
    const val MODEL_NAME = "gemini-3.8-flash"
  }

  private val model by lazy {
    Firebase.ai(backend = GenerativeBackend.googleAI())
        .generativeModel(
            modelName = modelName,
            // Gemini otherwise tends to wrap the reply in a markdown code fence, which
            // ResponseParser's strict JSON parsing does not tolerate.
            generationConfig = generationConfig { responseMimeType = "application/json" },
        )
  }

  override suspend fun generate(prompt: String, image: ImagePayload): String {
    if (!isOnline()) throw NetworkUnavailableException()
    val bitmap =
        BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size)
            ?: throw RejectedImageException(RejectionReason.Undecodable)

    val response =
        try {
          model.generateContent(photoAndPrompt(bitmap, prompt))
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          throw discoveryErrorFor(e)
        } finally {
          bitmap.recycle()
        }
    return response.text ?: throw BlockedException("the model returned no text")
  }

  private fun photoAndPrompt(bitmap: Bitmap, prompt: String) = content {
    image(bitmap)
    text(prompt)
  }

  private fun isOnline(): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
  }
}

/**
 * Maps an SDK or transport failure to the domain error the View can act on. Unknown errors are
 * rethrown so that bugs stay visible instead of being reported as network problems.
 */
internal fun discoveryErrorFor(error: Exception): DiscoveryException =
    when (error) {
      is ContentBlockedException,
      is PromptBlockedException,
      is ResponseStoppedException ->
          BlockedException(error.message ?: "blocked by the safety filter")
      is InvalidAPIKeyException,
      is ServiceDisabledException,
      is APINotConfiguredException,
      is UnsupportedUserLocationException,
      is InvalidLocationException,
      is PermissionMissingException ->
          ConfigurationException(error.message ?: "Firebase AI Logic is not configured", error)
      is QuotaExceededException,
      is ServerException,
      is RequestTimeoutException -> TransientException(error.message ?: "the model is busy", error)
      is FirebaseSerializationException -> ParseException(error)
      is IOException -> TransientException(error.message ?: "network error", error)
      is FirebaseAIException ->
          TransientException(error.message ?: "unknown Firebase AI error", error)
      else -> throw error
    }
