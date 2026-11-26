// Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
package com.github.roamswent.roam.model.location

import android.location.Address
import android.location.Geocoder
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowGeocoder

@RunWith(RobolectricTestRunner::class)
class AndroidLocationNameResolverTest {
  private val geocoder = Geocoder(ApplicationProvider.getApplicationContext(), Locale.ENGLISH)
  private val shadow = Shadow.extract<ShadowGeocoder>(geocoder)
  private val resolver = AndroidLocationNameResolver(geocoder)
  private val coordinates = Coordinates(48.86, 2.34)

  @After fun reset() = ShadowGeocoder.reset()

  @Test
  fun returnsCityAndCountry() = runTest {
    ShadowGeocoder.setIsPresent(true)
    shadow.setFromLocation(
        listOf(
            Address(Locale.ENGLISH).apply {
              locality = "Paris"
              countryName = "France"
            }
        )
    )
    assertEquals(LocationName("Paris", "France"), resolver.resolve(coordinates))
  }

  @Test
  fun returnsNullWithoutAServiceOrAddress() = runTest {
    ShadowGeocoder.setIsPresent(false)
    assertNull(resolver.resolve(coordinates))
    ShadowGeocoder.setIsPresent(true)
    shadow.setFromLocation(emptyList())
    assertNull(resolver.resolve(coordinates))
  }

  @Test
  fun usesSubAdminAreaWhenLocalityIsUnavailable() = runTest {
    ShadowGeocoder.setIsPresent(true)
    shadow.setFromLocation(
        listOf(
            Address(Locale.ENGLISH).apply {
              subAdminArea = "Paris"
              countryName = "France"
            }
        )
    )
    assertEquals(LocationName("Paris", "France"), resolver.resolve(coordinates))
  }

  @Test
  fun returnsNullWhenGeocodingFails() = runTest {
    ShadowGeocoder.setIsPresent(true)
    val failingGeocoder = mockk<Geocoder>()
    every { failingGeocoder.getFromLocation(48.86, 2.34, 1) } throws IOException("offline")
    assertNull(AndroidLocationNameResolver(failingGeocoder).resolve(coordinates))
  }

  @Test
  fun returnsNullWhenTheGeocoderHasNoResult() = runTest {
    ShadowGeocoder.setIsPresent(true)
    val emptyGeocoder = mockk<Geocoder>()
    every { emptyGeocoder.getFromLocation(48.86, 2.34, 1) } returns null
    assertNull(AndroidLocationNameResolver(emptyGeocoder).resolve(coordinates))
  }

  @Test
  fun returnsNullWhenAddressHasNoUsableName() = runTest {
    ShadowGeocoder.setIsPresent(true)
    shadow.setFromLocation(listOf(Address(Locale.ENGLISH).apply { locality = " " }))
    assertNull(resolver.resolve(coordinates))
  }

  @Test
  fun keepsAnAddressWithOnlyACountryOrOnlyACity() = runTest {
    ShadowGeocoder.setIsPresent(true)
    shadow.setFromLocation(listOf(Address(Locale.ENGLISH).apply { countryName = "France" }))
    assertEquals(LocationName("", "France"), resolver.resolve(coordinates))
    shadow.setFromLocation(listOf(Address(Locale.ENGLISH).apply { locality = "Paris" }))
    assertEquals(LocationName("Paris", ""), resolver.resolve(coordinates))
  }
}
