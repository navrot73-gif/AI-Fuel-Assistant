package com.navrot.aifuelassistant.ui.map.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Stateless поисковая панель АЗС.
 * [query] и [visible] управляются извне (coordinator), события — через лямбды.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MapSearchBar(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    AnimatedVisibility(visible = visible) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Поиск АЗС...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            singleLine = true,
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Text("✕")
                    }
                }
            }
        )
    }
}