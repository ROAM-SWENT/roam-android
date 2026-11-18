/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.explore

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Log
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import com.github.roamswent.roam.ui.theme.monumentMarkerBody
import com.github.roamswent.roam.ui.theme.monumentMarkerBorder
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ExploreMonumentMarker(
    monument: ExploreMonument,
    state: MarkerState,
    isSelected: Boolean,
    onClick: () -> Boolean,
) {
  val context = LocalContext.current
  val density = LocalDensity.current
  val placeholderPainter = rememberVectorPainter(Icons.Filled.AccountBalance)
  val pinColor = MaterialTheme.monumentMarkerBody.toArgb()
  val borderColor = MaterialTheme.monumentMarkerBorder.toArgb()
  val placeholderColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
  val thumbnailSize = (48 * density.density).toInt()
  val photoKey =
      remember(monument.guideId, monument.photoBytes) {
        "${monument.guideId}:${monument.photoBytes.size}:${monument.photoBytes.contentHashCode()}"
      }
  val request =
      remember(photoKey, thumbnailSize) {
        ImageRequest.Builder(context)
            .data(monument.photoBytes)
            .memoryCacheKey("$photoKey:$thumbnailSize")
            .size(thumbnailSize)
            .scale(Scale.FILL)
            .allowHardware(false)
            .build()
      }
  var photoBitmap by remember(photoKey) { mutableStateOf<Bitmap?>(null) }

  LaunchedEffect(photoKey) {
    photoBitmap =
        if (monument.photoBytes.isEmpty()) {
          null
        } else {
          withContext(Dispatchers.IO) {
            try {
              context.imageLoader.execute(request).drawable?.toBitmap()
            } catch (exception: CancellationException) {
              throw exception
            } catch (exception: Exception) {
              Log.w(
                  TAG,
                  "Failed to decode monument photo for ${monument.guideId}; using placeholder",
                  exception,
              )
              null
            }
          }
        }
  }

  val markerIcon =
      remember(
          photoBitmap,
          pinColor,
          borderColor,
          placeholderColor,
          density.density,
      ) {
        try {
          createMonumentMarkerBitmapDescriptor(
              thumbnail = photoBitmap,
              placeholderPainter = placeholderPainter,
              density = density,
              pinColor = pinColor,
              borderColor = borderColor,
              placeholderColor = placeholderColor,
          )
        } catch (exception: RuntimeException) {
          Log.e(TAG, "Failed to create monument marker for ${monument.guideId}", exception)
          null
        }
      }
  Marker(
      state = state,
      title = monument.name,
      icon = if (isSelected) null else markerIcon,
      anchor = Offset(0.5f, 1f),
      onClick = { _ -> onClick() },
  )
}

internal fun createMonumentMarkerBitmapDescriptor(
    thumbnail: Bitmap?,
    placeholderPainter: Painter,
    density: Density,
    pinColor: Int,
    borderColor: Int,
    placeholderColor: Int,
): BitmapDescriptor {
  val bitmap =
      createMonumentMarkerBitmap(
          thumbnail,
          placeholderPainter,
          density,
          pinColor,
          borderColor,
          placeholderColor,
      )
  return BitmapDescriptorFactory.fromBitmap(bitmap)
}

internal fun createMonumentMarkerBitmap(
    thumbnail: Bitmap?,
    placeholderPainter: Painter,
    density: Density,
    pinColor: Int,
    borderColor: Int,
    placeholderColor: Int,
): Bitmap {
  val scale = density.density
  val width = (64 * scale).toInt()
  val height = (70 * scale).toInt()
  val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)
  val centerX = width / 2f
  val circleRadius = 24 * scale
  val circleCenterY = circleRadius + 4 * scale
  val pinPath =
      Path().apply {
        moveTo(centerX, height.toFloat())
        cubicTo(
            centerX - 20 * scale,
            height - 18 * scale,
            centerX - 23 * scale,
            26 * scale,
            centerX,
            4 * scale,
        )
        cubicTo(
            centerX + 23 * scale,
            26 * scale,
            centerX + 20 * scale,
            height - 18 * scale,
            centerX,
            height.toFloat(),
        )
        close()
      }
  val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pinColor }
  canvas.drawPath(pinPath, pinPaint)

  canvas.save()
  canvas.clipPath(
      Path().apply {
        addCircle(centerX, circleCenterY, circleRadius - 3 * scale, Path.Direction.CW)
      }
  )
  if (thumbnail != null) {
    val sourceSize = minOf(thumbnail.width, thumbnail.height)
    val sourceLeft = (thumbnail.width - sourceSize) / 2
    val sourceTop = (thumbnail.height - sourceSize) / 2
    val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(
        thumbnail,
        android.graphics.Rect(
            sourceLeft,
            sourceTop,
            sourceLeft + sourceSize,
            sourceTop + sourceSize,
        ),
        RectF(
            centerX - circleRadius + 3 * scale,
            circleCenterY - circleRadius + 3 * scale,
            centerX + circleRadius - 3 * scale,
            circleCenterY + circleRadius - 3 * scale,
        ),
        bitmapPaint,
    )
  } else {
    val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = borderColor }
    canvas.drawCircle(
        centerX,
        circleCenterY,
        circleRadius - 3 * scale,
        placeholderPaint,
    )
    drawPainter(
        canvas = canvas,
        painter = placeholderPainter,
        density = density,
        center = Offset(centerX, circleCenterY),
        size = 28 * scale,
        tint = placeholderColor,
    )
  }
  canvas.restore()

  val borderPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * scale
        color = borderColor
      }
  canvas.drawCircle(centerX, circleCenterY, circleRadius - 1.5f * scale, borderPaint)
  return bitmap
}

private fun drawPainter(
    canvas: Canvas,
    painter: Painter,
    density: Density,
    center: Offset,
    size: Float,
    tint: Int,
) {
  CanvasDrawScope().draw(
      density = density,
      layoutDirection = androidx.compose.ui.unit.LayoutDirection.Ltr,
      canvas = ComposeCanvas(canvas),
      size = Size(size, size),
  ) {
    translate(center.x - size / 2, center.y - size / 2) {
      with(painter) {
        draw(
            size = Size(size, size),
            colorFilter = ColorFilter.tint(androidx.compose.ui.graphics.Color(tint)),
        )
      }
    }
  }
}

private const val TAG = "ExploreMonumentMarker"
