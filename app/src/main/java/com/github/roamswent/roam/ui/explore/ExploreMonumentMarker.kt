/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 * Co-authored-by: Codex <codex@openai.com>
 */

package com.github.roamswent.roam.ui.explore

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withClip
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import com.github.roamswent.roam.R
import com.github.roamswent.roam.model.profile.PublicProfile
import com.github.roamswent.roam.ui.components.profilePictureInitial
import com.github.roamswent.roam.ui.theme.accentContainer
import com.github.roamswent.roam.ui.theme.monumentMarkerBody
import com.github.roamswent.roam.ui.theme.monumentMarkerBorder
import com.github.roamswent.roam.ui.theme.onAccentContainer
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import kotlin.math.ceil
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
      remember(context, photoKey, thumbnailSize) {
        ImageRequest.Builder(context)
            .data(monument.photoBytes)
            .memoryCacheKey("$photoKey:$thumbnailSize")
            .size(thumbnailSize)
            .scale(Scale.FILL)
            .allowHardware(false)
            .build()
      }
  var photoBitmap by remember(request) { mutableStateOf<Bitmap?>(null) }

  LaunchedEffect(request) {
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

internal class FriendMarkerArtwork(
    val avatar: Bitmap?,
    val initial: String,
    createIcon: () -> BitmapDescriptor?,
) {
  val icon by lazy(LazyThreadSafetyMode.NONE, createIcon)
}

@Composable
internal fun rememberFriendMarkerArtwork(profile: PublicProfile?): FriendMarkerArtwork {
  val context = LocalContext.current
  val density = LocalDensity.current
  val pinColor = MaterialTheme.accentContainer.toArgb()
  val borderColor = MaterialTheme.monumentMarkerBorder.toArgb()
  val initialColor = MaterialTheme.onAccentContainer.toArgb()
  val initialSize = with(density) { MaterialTheme.typography.titleMedium.fontSize.toPx() }
  val avatarBytes = profile?.avatarBytes
  val thumbnailSize = (AVATAR_DIAMETER_DP * density.density).toInt().coerceAtLeast(1)
  var avatar by remember(avatarBytes, thumbnailSize) { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(context, avatarBytes, thumbnailSize) {
    if (avatarBytes == null || avatarBytes.isEmpty()) return@LaunchedEffect
    avatar =
        withContext(Dispatchers.IO) {
          try {
            val request =
                ImageRequest.Builder(context)
                    .data(avatarBytes)
                    .size(thumbnailSize)
                    .scale(Scale.FILL)
                    .allowHardware(false)
                    .build()
            context.imageLoader.execute(request).drawable?.toBitmap()
          } catch (exception: CancellationException) {
            throw exception
          } catch (exception: Exception) {
            Log.w(TAG, "Failed to decode Explore avatar", exception)
            null
          }
        }
  }
  val initial = remember(profile?.username) { profilePictureInitial(profile?.username.orEmpty()) }
  val loadedAvatar = avatar
  return remember(
      loadedAvatar,
      initial,
      initialSize,
      density,
      pinColor,
      borderColor,
      initialColor,
  ) {
    FriendMarkerArtwork(loadedAvatar, initial) {
      friendBitmapDescriptor {
        createMonumentMarkerBitmap(
            thumbnail = loadedAvatar,
            placeholderPainter = null,
            density = density,
            pinColor = pinColor,
            borderColor = borderColor,
            placeholderColor = initialColor,
            initial = initial,
            initialSize = initialSize,
        )
      }
    }
  }
}

@Composable
internal fun rememberFriendGroupIcon(
    avatars: List<FriendMarkerArtwork>,
    extra: Int,
): BitmapDescriptor? {
  val bitmap = rememberFriendAvatarStack(avatars, extra)
  return remember(bitmap) { bitmap?.let { friendBitmapDescriptor { it } } }
}

@Composable
internal fun rememberFriendAvatarStack(
    avatars: List<FriendMarkerArtwork>,
    extra: Int,
    size: Dp? = null,
): Bitmap? {
  val badge = if (extra > 0) stringResource(R.string.explore_friend_pin_overflow, extra) else ""
  val density = LocalDensity.current
  val badgeColor = MaterialTheme.onAccentContainer.toArgb()
  val pinColor = MaterialTheme.accentContainer.toArgb()
  val borderColor = MaterialTheme.monumentMarkerBorder.toArgb()
  val textSize = with(density) { MaterialTheme.typography.labelMedium.fontSize.toPx() }
  val initialSize = with(density) { MaterialTheme.typography.titleMedium.fontSize.toPx() }
  return remember(
      avatars,
      badge,
      density,
      badgeColor,
      pinColor,
      borderColor,
      textSize,
      initialSize,
      size,
  ) {
    tryFriendArtwork {
      val scale =
          if (size == null) density.density else density.density * size.value / AVATAR_DIAMETER_DP
      val diameter = AVATAR_DIAMETER_DP * scale
      val radius = diameter / 2
      val top = if (size == null) AVATAR_TOP_DP * scale else 0f
      val step = STACK_STEP_DP * scale
      val avatarsWidth = diameter + step * (avatars.size - 1)
      val textPaint =
          Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pinColor
            this.textSize = minOf(textSize, diameter / 2)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
          }
      val badgeWidth =
          maxOf(diameter / 2, textPaint.measureText(badge) + BADGE_PADDING_DP * scale * 2)
      val badgeLeft = avatarsWidth - BADGE_OVERLAP_DP * scale
      val width = if (badge.isEmpty()) avatarsWidth else badgeLeft + badgeWidth
      val height = if (size == null) MARKER_HEIGHT_DP * scale else diameter
      val bitmapHeight = if (size == null) height.toInt() else ceil(height).toInt()
      val bitmap = createBitmap(ceil(width).toInt(), bitmapHeight)
      val canvas = Canvas(bitmap)
      val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
      val centerX = bitmap.width / 2f
      val centerY = top + radius
      paint.color = pinColor
      if (size == null)
          canvas.drawPath(monumentPinPath(centerX, bitmap.height.toFloat(), scale), paint)
      val bottom = top + diameter
      val borderWidth = MARKER_BORDER_DP * scale
      val inset = borderWidth / 2
      val borderPaint =
          Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = borderWidth
            color = borderColor
          }
      // Connect the outer borders behind the circular avatars.
      canvas.drawRoundRect(0f, top, avatarsWidth, bottom, radius, radius, paint)
      canvas.drawLine(radius, top + inset, avatarsWidth - radius, top + inset, borderPaint)
      canvas.drawLine(radius, bottom - inset, avatarsWidth - radius, bottom - inset, borderPaint)
      val initialPaint =
          Paint(textPaint).apply {
            color = badgeColor
            this.textSize = initialSize.coerceAtMost(radius - borderWidth)
          }
      for (index in avatars.indices.reversed()) {
        val artwork = avatars[index]
        val left = step * index
        val avatarCenterX = left + radius
        canvas.drawCircle(avatarCenterX, centerY, radius, paint)
        val avatar = artwork.avatar
        if (avatar != null) {
          val circle =
              Path().apply { addCircle(avatarCenterX, centerY, radius - inset, Path.Direction.CW) }
          canvas.withClip(circle) {
            drawSquareThumbnail(avatar, RectF(left, top, left + diameter, bottom), paint)
          }
        } else {
          val baseline = centerY - (initialPaint.ascent() + initialPaint.descent()) / 2
          canvas.drawText(artwork.initial, avatarCenterX, baseline, initialPaint)
        }
        canvas.drawCircle(avatarCenterX, centerY, radius - inset, borderPaint)
      }
      if (badge.isNotEmpty()) {
        val badgeRadius = diameter * BADGE_RADIUS_FRACTION
        paint.color = badgeColor
        canvas.drawRoundRect(
            badgeLeft,
            centerY - badgeRadius,
            width,
            centerY + badgeRadius,
            badgeRadius,
            badgeRadius,
            paint,
        )
        val baseline = centerY - (textPaint.ascent() + textPaint.descent()) / 2
        canvas.drawText(badge, badgeLeft + badgeWidth / 2, baseline, textPaint)
      }
      bitmap
    }
  }
}

private inline fun friendBitmapDescriptor(render: () -> Bitmap): BitmapDescriptor? =
    tryFriendArtwork {
      BitmapDescriptorFactory.fromBitmap(render())
    }

private inline fun <T> tryFriendArtwork(render: () -> T): T? =
    try {
      render()
    } catch (exception: RuntimeException) {
      Log.e(TAG, "Failed to create Explore avatar marker", exception)
      null
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
    placeholderPainter: Painter?,
    density: Density,
    pinColor: Int,
    borderColor: Int,
    placeholderColor: Int,
    initial: String = "",
    initialSize: Float = 0f,
): Bitmap {
  val scale = density.density
  val width = (64 * scale).toInt()
  val height = (MARKER_HEIGHT_DP * scale).toInt()
  val bitmap = createBitmap(width, height)
  val canvas = Canvas(bitmap)
  val centerX = width / 2f
  val circleRadius = AVATAR_DIAMETER_DP * scale / 2
  val circleCenterY = circleRadius + AVATAR_TOP_DP * scale
  val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pinColor }
  canvas.drawPath(monumentPinPath(centerX, height.toFloat(), scale), pinPaint)

  val borderWidth = MARKER_BORDER_DP * scale
  val thumbnailRadius = circleRadius - borderWidth
  if (thumbnail != null) {
    val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val thumbnailPath =
        Path().apply { addCircle(centerX, circleCenterY, thumbnailRadius, Path.Direction.CW) }
    canvas.withClip(thumbnailPath) {
      drawSquareThumbnail(
          thumbnail,
          RectF(
              centerX - thumbnailRadius,
              circleCenterY - thumbnailRadius,
              centerX + thumbnailRadius,
              circleCenterY + thumbnailRadius,
          ),
          bitmapPaint,
      )
    }
  } else {
    canvas.drawCircle(centerX, circleCenterY, thumbnailRadius, pinPaint)
    if (placeholderPainter != null) {
      drawPainter(
          canvas = canvas,
          painter = placeholderPainter,
          density = density,
          center = Offset(centerX, circleCenterY),
          size = 28 * scale,
          tint = placeholderColor,
      )
    } else {
      val initialPaint =
          Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = placeholderColor
            textAlign = Paint.Align.CENTER
            textSize = initialSize.coerceAtMost(thumbnailRadius)
            typeface = Typeface.DEFAULT_BOLD
          }
      val baseline = circleCenterY - (initialPaint.ascent() + initialPaint.descent()) / 2
      canvas.drawText(initial, centerX, baseline, initialPaint)
    }
  }
  val borderPaint =
      Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
        color = borderColor
      }
  canvas.drawCircle(centerX, circleCenterY, circleRadius - borderWidth / 2, borderPaint)
  return bitmap
}

private fun Canvas.drawSquareThumbnail(thumbnail: Bitmap, destination: RectF, paint: Paint) {
  val size = minOf(thumbnail.width, thumbnail.height)
  val left = (thumbnail.width - size) / 2
  val top = (thumbnail.height - size) / 2
  drawBitmap(thumbnail, Rect(left, top, left + size, top + size), destination, paint)
}

private fun monumentPinPath(centerX: Float, height: Float, scale: Float): Path =
    Path().apply {
      moveTo(centerX, height)
      cubicTo(
          centerX - 20 * scale,
          height - 18 * scale,
          centerX - 23 * scale,
          26 * scale,
          centerX,
          AVATAR_TOP_DP * scale,
      )
      cubicTo(
          centerX + 23 * scale,
          26 * scale,
          centerX + 20 * scale,
          height - 18 * scale,
          centerX,
          height,
      )
      close()
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
private const val AVATAR_DIAMETER_DP = 48
private const val AVATAR_TOP_DP = 4
private const val MARKER_HEIGHT_DP = 70
private const val MARKER_BORDER_DP = 3
private const val STACK_STEP_DP = 26
private const val BADGE_OVERLAP_DP = 8
private const val BADGE_PADDING_DP = 6
private const val BADGE_RADIUS_FRACTION = 0.32f
