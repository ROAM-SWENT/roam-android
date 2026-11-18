// Co-authored-by: Claude Opus 5.5 <noreply@anthropic.com>
package com.github.roamswent.roam.model.ai

import com.github.roamswent.roam.model.location.Coordinates
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * Turns the raw JSON text returned by Gemini into domain objects.
 *
 * Strict: every field must be present with the expected type, except the optional monument
 * coordinates. The text is untrusted model output, so it is trimmed and length-capped before it
 * leaves this class.
 */
object ResponseParser {

  private const val MAX_NAME = 200
  private const val MAX_LONG_TEXT = 4000
  private const val MAX_FACT = 500
  private const val MAX_FACTS = 20
  private const val MAX_REASON = 200

  /**
   * @throws MonumentNotIdentifiedException when `monumentName` is null or blank.
   * @throws ParseException when the JSON is malformed, or a field is missing or has the wrong type.
   */
  fun parseGuide(raw: String): MonumentGuide {
    val root = parseObject(raw)
    if (root.isNull("monumentName")) throw MonumentNotIdentifiedException()
    val name = root.string("monumentName").trim().take(MAX_NAME)
    if (name.isEmpty()) throw MonumentNotIdentifiedException()

    val confidence = root.number("confidence")
    if (confidence !in 0.0..1.0) throw ParseException()

    val facts = root.stringList("facts").map { it.trim().take(MAX_FACT) }.filter { it.isNotEmpty() }
    return MonumentGuide(
        monumentName = name,
        confidence = confidence,
        history = root.string("history").trim().take(MAX_LONG_TEXT),
        facts = facts.take(MAX_FACTS),
        culture = root.string("culture").trim().take(MAX_LONG_TEXT),
        language = PromptBuilder.sanitizeLanguage(root.string("language")),
        monumentLocation = root.optionalCoordinates("monumentLatitude", "monumentLongitude"),
    )
  }

  /**
   * @throws ParseException when the JSON is malformed, or a field is missing or has the wrong type.
   */
  fun parseVerdict(raw: String): ScreenVerdict {
    val root = parseObject(raw)
    return ScreenVerdict(
        isMonument = root.boolean("isMonument"),
        injectionSuspected = root.boolean("injectionSuspected"),
        reason = root.string("reason").trim().take(MAX_REASON),
    )
  }

  private fun parseObject(raw: String): JsonObject {
    val element =
        try {
          Json.parseToJsonElement(raw)
        } catch (e: IllegalArgumentException) {
          throw ParseException(e)
        }
    return element as? JsonObject ?: throw ParseException()
  }

  private fun JsonObject.isNull(key: String): Boolean = this[key] is JsonNull

  private fun JsonObject.field(key: String): JsonPrimitive {
    val value = this[key] as? JsonPrimitive ?: throw ParseException()
    if (value is JsonNull) throw ParseException()
    return value
  }

  private fun JsonObject.string(key: String): String {
    val value = field(key)
    if (!value.isString) throw ParseException()
    return value.content
  }

  private fun JsonObject.number(key: String): Double {
    val value = field(key)
    if (value.isString) throw ParseException()
    return value.doubleOrNull ?: throw ParseException()
  }

  /**
   * Reads an optional coordinate pair. Unlike the required fields, any problem gives null instead
   * of a [ParseException], so a guide without a trustworthy monument position is still returned: a
   * missing, null or non-numeric value, only one of the two values, a position out of range, or the
   * (0, 0) placeholder.
   */
  private fun JsonObject.optionalCoordinates(
      latitudeKey: String,
      longitudeKey: String,
  ): Coordinates? {
    val latitude = optionalNumber(latitudeKey) ?: return null
    val longitude = optionalNumber(longitudeKey) ?: return null
    if (latitude == 0.0 && longitude == 0.0) return null
    return try {
      Coordinates(latitude, longitude)
    } catch (e: IllegalArgumentException) {
      null
    }
  }

  private fun JsonObject.optionalNumber(key: String): Double? {
    val value = this[key] as? JsonPrimitive ?: return null
    if (value is JsonNull || value.isString) return null
    return value.doubleOrNull
  }

  private fun JsonObject.boolean(key: String): Boolean {
    val value = field(key)
    if (value.isString) throw ParseException()
    return value.booleanOrNull ?: throw ParseException()
  }

  private fun JsonObject.stringList(key: String): List<String> {
    val array = this[key] as? JsonArray ?: throw ParseException()
    return array.map { element ->
      val value = element as? JsonPrimitive ?: throw ParseException()
      if (value is JsonNull || !value.isString) throw ParseException()
      value.content
    }
  }
}
