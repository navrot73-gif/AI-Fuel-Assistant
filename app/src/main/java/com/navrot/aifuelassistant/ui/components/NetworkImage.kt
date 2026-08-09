package com.navrot.aifuelassistant.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Обёртка для загрузки изображения по URL с помощью Coil Compose.
 * Безопаснее и эффективнее, чем самописный загрузчик.
 */
@Composable
fun NetworkImage(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    if (url.isBlank()) {
        Surface(modifier = modifier, color = MaterialTheme.colorScheme.surfaceVariant) { Box(modifier = Modifier.fillMaxSize()) }
        return
    }

    val ctx = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(ctx)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}
