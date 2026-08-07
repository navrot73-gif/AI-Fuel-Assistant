package com.navrot.aifuelassistant.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * Простая загрузка картинки по URL без Coil/Glide.
 * Пустой URL → ничего не рисуется.
 * Кеш в памяти через companion object.
 */
@Composable
fun NetworkImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    if (url.isBlank()) return

    var bitmap by remember(url) { mutableStateOf<Bitmap?>(ImageCache[url]) }

    LaunchedEffect(url) {
        ImageCache[url]?.let { bitmap = it; return@LaunchedEffect }
        withContext(Dispatchers.IO) {
            try {
                val bmp = URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                if (bmp != null) {
                    ImageCache[url] = bmp
                    withContext(Dispatchers.Main) { bitmap = bmp }
                }
            } catch (_: Exception) {
                // Ошибка — ничего не рисуем
            }
        }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        // Placeholder во время загрузки
        Surface(
            modifier = modifier,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) { Box(modifier = Modifier) {} }
    }
}

private object ImageCache {
    private val map = ConcurrentHashMap<String, Bitmap>()
    operator fun get(url: String): Bitmap? = map[url]
    operator fun set(url: String, bmp: Bitmap) { map[url] = bmp }
}
