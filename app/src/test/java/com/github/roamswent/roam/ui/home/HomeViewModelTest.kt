package com.github.roamswent.roam.ui.home

import org.junit.Assert.assertNotNull
import org.junit.Test

class HomeViewModelTest {
  @Test
  fun constructorCreatesViewModel() {
    assertNotNull(HomeViewModel())
  }

  @Test
  fun scanClickCanBeHandledRepeatedly() {
    val viewModel = HomeViewModel()

    viewModel.onScanClicked()
    viewModel.onScanClicked()
  }
}
