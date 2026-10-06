package com.github.roamswent.roam.model.ai

/**
 * Every failure of the discovery pipeline. Thrown rather than returned, so the View can catch and
 * display each case later. Kotlin has no checked exceptions, so callers must catch these
 * explicitly.
 */
sealed class DiscoveryException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** The device is offline. Nothing is uploaded. */
class NetworkUnavailableException : DiscoveryException("No network connection")

/** The image failed local validation or the injection screen. */
class RejectedImageException(val reason: RejectionReason) :
    DiscoveryException("Image rejected: $reason")

/** The model found no monument in the photo. */
class MonumentNotIdentifiedException : DiscoveryException("No monument identified in the photo")

/** The model blocked the request (safety filter or empty candidates). */
class BlockedException(detail: String) : DiscoveryException("Request blocked: $detail")

/** The model answered, but not in the expected JSON shape, even after a retry. */
class ParseException(cause: Throwable? = null) :
    DiscoveryException("Model response could not be parsed", cause)

/** App Check or the Gemini API is not set up. Developer-facing, not a user error. */
class ConfigurationException(message: String, cause: Throwable? = null) :
    DiscoveryException(message, cause)

/** Rate limit or timeout that persisted after the bounded retries. */
class TransientException(message: String, cause: Throwable? = null) :
    DiscoveryException(message, cause)

enum class RejectionReason {
  Oversized,
  UnsupportedFormat,
  MimeMismatch,
  Undecodable,
  Animated,
  DimensionsOutOfRange,
  InjectionSuspected,
}
