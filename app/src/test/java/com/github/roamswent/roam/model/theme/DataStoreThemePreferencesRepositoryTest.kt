/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.model.theme

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreThemePreferencesRepositoryTest {
  @get:Rule val temporaryFolder = TemporaryFolder()

  @Test
  fun themeMode_defaultsToAutoAndPersistsSelection() = runBlocking {
    val file = File(temporaryFolder.root, "theme.preferences_pb")
    val firstJob = SupervisorJob()
    val firstScope = CoroutineScope(firstJob + Dispatchers.IO)

    try {
      val firstDataStore =
          PreferenceDataStoreFactory.create(
              scope = firstScope,
              produceFile = { file },
          )
      val firstRepository = DataStoreThemePreferencesRepository(firstDataStore)
      assertEquals(ThemeMode.AUTO, firstRepository.themeMode.first())

      firstRepository.setThemeMode(ThemeMode.DARK)
    } finally {
      firstJob.cancelAndJoin()
    }

    val restoredJob = SupervisorJob()
    val restoredScope = CoroutineScope(restoredJob + Dispatchers.IO)
    try {
      val restoredDataStore =
          PreferenceDataStoreFactory.create(
              scope = restoredScope,
              produceFile = { file },
          )
      val restoredRepository = DataStoreThemePreferencesRepository(restoredDataStore)
      assertEquals(ThemeMode.DARK, restoredRepository.themeMode.first())
    } finally {
      restoredJob.cancelAndJoin()
    }
  }
}
