package com.github.se.bootcamp.model.todo

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateParser {

  private val dateRegex = Regex("""^\d{2}/\d{2}/\d{4}$""")
  private val dateFormat =
      SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).let {
        it.isLenient = false
        it
      }

  fun parse(str: String): Date? {
    if (!dateRegex.matches(str)) {
      return null
    }

    return runCatching { dateFormat.parse(str) }.getOrNull()
  }
}
