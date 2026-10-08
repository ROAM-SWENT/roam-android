package com.github.roamswent.roam.ui.camera

import android.Manifest
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.roamswent.roam.MainActivity
import com.github.roamswent.roam.data.FileCapturedImageManager
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CameraXEngineDeviceTest {
  @get:Rule
  val cameraPermissionRule: GrantPermissionRule =
      GrantPermissionRule.grant(Manifest.permission.CAMERA)

  @Test
  fun realEngineBindsCapturesToFileAndStopsCoordinator() {
    val saved = CountDownLatch(1)
    val error = arrayOfNulls<Throwable>(1)
    var targetUri: android.net.Uri? = null
    var coordinator: CameraCoordinator? = null

    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
      scenario.onActivity { activity ->
        val previewView = PreviewView(activity)
        activity.addContentView(
            previewView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1),
        )
        coordinator = CameraCoordinator(activity, activity, CameraXEngine())
        coordinator!!.start(previewView)
      }

      assertTrue(waitUntil { coordinator?.isRunning == true })
      assertNotNull(coordinator)

      scenario.onActivity { activity ->
        targetUri = runBlocking {
          FileCapturedImageManager(activity, Dispatchers.IO).newCaptureUri()
        }
        coordinator!!.captureTo(targetUri!!, saved::countDown) { throwable ->
          error[0] = throwable
          saved.countDown()
        }
      }

      assertTrue(saved.await(10, TimeUnit.SECONDS))
      assertTrue("capture failed: ${error[0]}", error[0] == null)
      assertTrue(File(activityCacheDir(scenario), "captures/capture.jpg").length() > 0)

      scenario.onActivity { coordinator!!.stop() }
      assertFalse(coordinator!!.isRunning)
    }
  }

  private fun activityCacheDir(scenario: ActivityScenario<MainActivity>): File {
    var cacheDir: File? = null
    scenario.onActivity { cacheDir = it.cacheDir }
    return cacheDir!!
  }

  private fun waitUntil(condition: () -> Boolean): Boolean {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
    while (System.nanoTime() < deadline) {
      if (condition()) return true
      Thread.sleep(50)
    }
    return condition()
  }
}
