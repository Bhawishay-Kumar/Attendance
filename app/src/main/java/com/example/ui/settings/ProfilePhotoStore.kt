package com.example.ui.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Copies a Photo-Picker image into app-private storage (no storage permission needed).
 * Handles custom user avatar storage, retrieval, clearing, and existence checks.
 */
object ProfilePhotoStore {

    private val bitmapCache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()
    val assetLogoCache = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

    /** Returns the most recently saved profile photo File, if one exists. */
    fun getLatestPhotoFile(context: Context): File? {
        val files = context.filesDir.listFiles { _, name ->
            name.startsWith("profile_photo_") && name.endsWith(".jpg")
        } ?: return null
        return files.filter { it.length() > 0 }.maxByOrNull { it.lastModified() }
    }


    /** Loads the user's custom photo as a scaled Android Bitmap, or null if using preset. */
    fun loadBitmap(context: Context, maxEdgePx: Int = 512): Bitmap? {
        val f = getLatestPhotoFile(context) ?: return null
        if (!f.exists() || f.length() == 0L) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.absolutePath, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdgePx) sample *= 2
            BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    /** Returns the absolute path of the saved 512x512 JPEG, or null on failure. */
    suspend fun save(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
            val decoded = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null

            // EXIF orientation correction
            val rotation = runCatching {
                resolver.openInputStream(uri)?.use {
                    when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                        else -> 0f
                    }
                } ?: 0f
            }.getOrDefault(0f)
            val upright = if (rotation != 0f) {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation) }, true)
            } else decoded

            val side = minOf(upright.width, upright.height)
            val square = Bitmap.createBitmap(upright, (upright.width - side) / 2, (upright.height - side) / 2, side, side)
            val out = Bitmap.createScaledBitmap(square, 512, 512, true)

            // Remove any previous custom photo files
            clear(context)

            val file = File(context.filesDir, "profile_photo_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            val path = file.absolutePath
            val loaded = out.asImageBitmap()
            bitmapCache[path] = loaded
            path
        }.getOrNull()
    }

    suspend fun saveFromUri(context: Context, uri: Uri): String? = save(context, uri)

    /** Removes all saved custom profile photos from internal storage. */
    fun clear(context: Context) {
        bitmapCache.clear()
        runCatching {
            context.filesDir.listFiles { _, name ->
                name.startsWith("profile_photo_")
            }?.forEach { it.delete() }
        }
    }

    /** Deletes a specific photo path if it matches the profile photo naming pattern. */
    fun delete(path: String) {
        if (path.isBlank()) return
        bitmapCache.remove(path)
        runCatching {
            val f = File(path)
            if (f.exists() && f.name.startsWith("profile_photo_")) f.delete()
        }
    }

    /** Loads an ImageBitmap directly from a file path with in-memory caching. */
    fun load(path: String): ImageBitmap? {
        if (path.isBlank()) return null
        return bitmapCache.getOrPut(path) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() ?: return null
        }
    }
}


/**
 * Loads the real uploaded BK Logo image from either `res/drawable/` or `assets/`
 * so the actual attached PNG/JPG files (`1000096716.png` & `1000090211.jpg`) are displayed.
 */
@Composable
fun BkBrandLogoImage(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Crossfade(
        targetState = isDark,
        animationSpec = tween(durationMillis = 400),
        label = "bkRealLogoCrossfade",
        modifier = modifier
    ) { darkTheme ->
        val drawableResId = remember(darkTheme) {
            val resName = if (darkTheme) "bk_logo_dark" else "bk_logo_light"
            context.resources.getIdentifier(resName, "drawable", context.packageName)
        }

        val assetBitmap: ImageBitmap? = remember(darkTheme) {
            if (drawableResId != 0) null
            else {
                val cacheKey = if (darkTheme) "dark_logo" else "light_logo"
                ProfilePhotoStore.assetLogoCache.getOrPut(cacheKey) {
                    val candidateFiles = if (darkTheme) {
                        listOf("bk_logo_dark.png", "1000096716.png")
                    } else {
                        listOf("bk_logo_light.jpg", "bk_logo_light.png", "1000090211.jpg")
                    }
                    var loaded: ImageBitmap? = null
                    for (name in candidateFiles) {
                        try {
                            context.assets.open(name).use { stream ->
                                loaded = BitmapFactory.decodeStream(stream)?.asImageBitmap()
                            }
                            if (loaded != null) break
                        } catch (_: Exception) {}
                    }
                    loaded ?: return@remember null
                }
            }
        }

        when {
            drawableResId != 0 -> {
                Image(
                    painter = painterResource(id = drawableResId),
                    contentDescription = "Crowned BK Monogram Emblem",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            assetBitmap != null -> {
                Image(
                    bitmap = assetBitmap,
                    contentDescription = "Crowned BK Monogram Emblem",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                // Fallback only if neither drawable nor asset file is found
                BkMonogramCanvasEmblem(
                    isDark = darkTheme,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Universal Profile Avatar Composable to be used across Settings, Home Header, and Attendance Header.
 * - If the user has selected a custom photo in Settings (`ProfilePhotoStore`), renders their custom Bitmap.
 * - Otherwise, renders the theme-aware BK Monogram Preset (`1000096716.png` in Dark / `1000090211.jpg` in Light).
 */
@Composable
fun GlobalProfileAvatar(
    photoVersion: Int = 0,
    photoPath: String? = null,
    isDark: Boolean,
    size: Dp = 44.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val customBitmap = remember(photoVersion, photoPath) {
        if (!photoPath.isNullOrBlank()) {
            ProfilePhotoStore.load(photoPath)
        } else {
            ProfilePhotoStore.getLatestPhotoFile(context)?.absolutePath?.let { ProfilePhotoStore.load(it) }
        }
    }

    val ringBrush = if (isDark) {
        Brush.sweepGradient(
            listOf(
                Color(0xFFA855F7), // Electric Purple
                Color(0xFF2563EB), // Royal Blue
                Color(0xFF00F0FF), // Neon Cyan
                Color(0xFF3B82F6),
                Color(0xFFA855F7)
            )
        )
    } else {
        Brush.linearGradient(
            listOf(
                Color(0xFF0A248A), // Deep Navy Blue
                Color(0xFF0077FF), // Vivid Royal Blue
                Color(0xFF00D2FF)  // Bright Cyan
            )
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .border(1.8.dp, ringBrush, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap,
                contentDescription = "User Profile Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        } else {
            BkBrandLogoImage(
                isDark = isDark,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
    }
}

/**
 * Exact Canvas vector recreation of attached `1000096716.png` (Dark Cosmic Nebula & Neon Glow)
 * and `1000090211.jpg` (Light Alabaster & Royal-Cyan Gradient) Crowned "BK" Monogram Emblem.
 */
@Composable
fun BkMonogramCanvasEmblem(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = size.minDimension / 2f

        // 1. Background Canvas (Cosmic Deep Space in Dark Mode / Soft Alabaster in Light Mode)
        if (isDark) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF090F2C),
                        Color(0xFF04071A),
                        Color(0xFF02030D)
                    ),
                    center = Offset(cx, cy),
                    radius = r * 1.15f
                )
            )
            // Purple Nebula Glow (bottom-left & top-left)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF9333EA).copy(alpha = 0.42f), Color.Transparent),
                    center = Offset(w * 0.22f, h * 0.78f),
                    radius = r * 0.75f
                ),
                center = Offset(w * 0.22f, h * 0.78f),
                radius = r * 0.75f
            )
            // Cyan Nebula Glow (top-right & bottom-right)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF06B6D4).copy(alpha = 0.38f), Color.Transparent),
                    center = Offset(w * 0.80f, h * 0.28f),
                    radius = r * 0.75f
                ),
                center = Offset(w * 0.80f, h * 0.28f),
                radius = r * 0.75f
            )
        } else {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF),
                        Color(0xFFF8F9FC),
                        Color(0xFFF1F4F9)
                    ),
                    center = Offset(cx, cy),
                    radius = r * 1.1f
                )
            )
        }

        // 2. Main Gradient Brush (Deep Navy/Violet -> Royal Blue -> Electric Cyan)
        val brandBrush = if (isDark) {
            Brush.linearGradient(
                0.0f to Color(0xFFB833FF), // Neon Violet-Magenta
                0.35f to Color(0xFF2563EB), // Royal Blue
                0.70f to Color(0xFF00B4FF), // Electric Sky
                1.0f to Color(0xFF00F5FF), // Luminous Cyan
                start = Offset(w * 0.15f, h * 0.75f),
                end = Offset(w * 0.85f, h * 0.25f)
            )
        } else {
            Brush.linearGradient(
                0.0f to Color(0xFF071666), // Deep Midnight Navy
                0.45f to Color(0xFF0055E5), // Vivid Royal Blue
                1.0f to Color(0xFF00C8F8), // Bright Aqua Cyan
                start = Offset(w * 0.15f, h * 0.75f),
                end = Offset(w * 0.85f, h * 0.25f)
            )
        }

        // 3. Split Circular Brush-Stroke Ring (with signature gaps at bottom-left & top-right)
        val ringRadius = r * 0.80f
        val ringStroke = r * 0.048f
        val arcRect = Rect(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)

        if (isDark) {
            // Outer Neon Halo on Ring
            drawArc(
                brush = brandBrush,
                startAngle = 148f,
                sweepAngle = 186f,
                useCenter = false,
                topLeft = arcRect.topLeft,
                size = arcRect.size,
                style = Stroke(width = ringStroke * 2.2f, cap = StrokeCap.Round),
                alpha = 0.28f
            )
            drawArc(
                brush = brandBrush,
                startAngle = 342f,
                sweepAngle = 152f,
                useCenter = false,
                topLeft = arcRect.topLeft,
                size = arcRect.size,
                style = Stroke(width = ringStroke * 2.2f, cap = StrokeCap.Round),
                alpha = 0.28f
            )
        }

        // Upper-Left Arc
        drawArc(
            brush = brandBrush,
            startAngle = 146f,
            sweepAngle = 188f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = ringStroke, cap = StrokeCap.Round)
        )
        // Lower-Right Arc
        drawArc(
            brush = brandBrush,
            startAngle = 342f,
            sweepAngle = 154f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = ringStroke, cap = StrokeCap.Round)
        )

        // 4. Three-Peak Royal Crown above "BK"
        val crownPath = Path().apply {
            val baseLeft = w * 0.445f
            val baseRight = w * 0.545f
            val baseY = h * 0.278f
            val leftPeakX = w * 0.428f
            val leftPeakY = h * 0.218f
            val midPeakX = w * 0.498f
            val midPeakY = h * 0.185f
            val rightPeakX = w * 0.568f
            val rightPeakY = h * 0.220f

            moveTo(baseLeft, baseY)
            lineTo(leftPeakX, leftPeakY)
            quadraticTo(w * 0.465f, h * 0.258f, midPeakX, midPeakY)
            quadraticTo(w * 0.530f, h * 0.258f, rightPeakX, rightPeakY)
            lineTo(baseRight, baseY)
            quadraticTo(w * 0.495f, h * 0.266f, baseLeft, baseY)
            close()
        }
        drawPath(path = crownPath, brush = brandBrush)
        // 3 Crown Pearls
        drawCircle(brush = brandBrush, radius = r * 0.022f, center = Offset(w * 0.426f, h * 0.208f))
        drawCircle(brush = brandBrush, radius = r * 0.025f, center = Offset(w * 0.498f, h * 0.173f))
        drawCircle(brush = brandBrush, radius = r * 0.022f, center = Offset(w * 0.570f, h * 0.210f))

        // 5. Calligraphic "BK" Monogram + Signature Underline Swoosh
        drawIntoCanvas { canvas ->
            val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                isFakeBoldText = true
                textSkewX = -0.22f
                textAlign = android.graphics.Paint.Align.CENTER
                textSize = r * 0.78f
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.SERIF,
                    android.graphics.Typeface.BOLD_ITALIC
                )
                shader = android.graphics.LinearGradient(
                    w * 0.20f, h * 0.70f,
                    w * 0.80f, h * 0.30f,
                    if (isDark) intArrayOf(0xFFB833FF.toInt(), 0xFF2563EB.toInt(), 0xFF00F5FF.toInt())
                    else intArrayOf(0xFF071666.toInt(), 0xFF0055E5.toInt(), 0xFF00C8F8.toInt()),
                    floatArrayOf(0f, 0.5f, 1f),
                    android.graphics.Shader.TileMode.CLAMP
                )
                if (isDark) {
                    setShadowLayer(r * 0.08f, 0f, 0f, 0xAA00D2FF.toInt())
                } else {
                    setShadowLayer(r * 0.04f, 0f, r * 0.02f, 0x33071666)
                }
            }
            canvas.nativeCanvas.drawText("BK", cx, cy + r * 0.20f, textPaint)
        }

        // Signature Calligraphic Underline Swoosh
        val swooshPath = Path().apply {
            moveTo(w * 0.27f, h * 0.725f)
            cubicTo(
                w * 0.44f, h * 0.655f,
                w * 0.65f, h * 0.590f,
                w * 0.735f, h * 0.648f
            )
            cubicTo(
                w * 0.64f, h * 0.625f,
                w * 0.45f, h * 0.695f,
                w * 0.27f, h * 0.725f
            )
            close()
        }
        drawPath(path = swooshPath, brush = brandBrush)
    }
}
