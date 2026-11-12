// Co-authored-by: Google Antigravity <noreply@google.com>

package com.github.roamswent.roam

import androidx.core.os.LocaleListCompat
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class RoamApplicationTest {

  @Test
  fun snapDefaultLocale_emptyLocalesSnapsToFrenchWhenDefaultIsFrench() {
    var appliedTag: String? = null
    RoamApplication.snapDefaultLocale(
        currentLocales = LocaleListCompat.getEmptyLocaleList(),
        defaultLocale = Locale.FRENCH,
        setLocales = { appliedTag = it.toLanguageTags() },
    )
    assertEquals("fr", appliedTag)
  }

  @Test
  fun snapDefaultLocale_emptyLocalesSnapsToEnglishWhenDefaultIsNotFrench() {
    var appliedTag: String? = null
    RoamApplication.snapDefaultLocale(
        currentLocales = LocaleListCompat.getEmptyLocaleList(),
        defaultLocale = Locale.GERMAN,
        setLocales = { appliedTag = it.toLanguageTags() },
    )
    assertEquals("en", appliedTag)
  }

  @Test
  fun snapDefaultLocale_doesNotOverrideExistingLocales() {
    var wasCalled = false
    RoamApplication.snapDefaultLocale(
        currentLocales = LocaleListCompat.forLanguageTags("en"),
        defaultLocale = Locale.FRENCH,
        setLocales = { wasCalled = true },
    )
    org.junit.Assert.assertFalse(wasCalled)
  }
}
