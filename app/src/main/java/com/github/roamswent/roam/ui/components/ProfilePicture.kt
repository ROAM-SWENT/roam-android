/*
 * Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>
 */

package com.github.roamswent.roam.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.github.roamswent.roam.ui.theme.HomeAccentGreen
import com.github.roamswent.roam.ui.theme.OnBrand
import kotlin.math.roundToInt

@Composable
internal fun ProfilePicture(
    name: String,
    avatarBytes: ByteArray? = null,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleMedium,
    onClick: (() -> Unit)? = null,
) {
  val clickableModifier = onClick?.let { modifier.clickable(onClick = it) } ?: modifier
  Box(
      modifier = clickableModifier.clip(CircleShape).background(HomeAccentGreen),
      contentAlignment = Alignment.Center,
  ) {
    if (avatarBytes != null) {
      val avatar = BitmapFactory.decodeByteArray(avatarBytes, 0, avatarBytes.size)
      if (avatar != null) {
        androidx.compose.foundation.Image(
            bitmap = avatar.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().clip(CircleShape),
        )
      } else {
        Text(
            text = profilePictureInitial(name),
            color = OnBrand,
            style = textStyle,
            fontWeight = FontWeight.Bold,
        )
      }
    } else {
      Text(
          text = profilePictureInitial(name),
          color = OnBrand,
          style = textStyle,
          fontWeight = FontWeight.Bold,
      )
    }
  }
}

internal fun profilePictureInitial(name: String): String =
    name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

internal object ProfilePictureBitmap {
  fun create(context: Context, name: String): Bitmap {
    val density = context.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
    val size = (64 * density).roundToInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val center = size / 2f
    paint.color = HomeAccentGreen.toArgb()
    canvas.drawCircle(center, center, center, paint)

    paint.color = OnBrand.toArgb()
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = size * 0.38f
    paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
    val baseline = center - (paint.ascent() + paint.descent()) / 2
    canvas.drawText(profilePictureInitial(name), center, baseline, paint)
    return bitmap
  }
}
