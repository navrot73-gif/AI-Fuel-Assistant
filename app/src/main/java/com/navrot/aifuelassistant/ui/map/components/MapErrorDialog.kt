package com.navrot.aifuelassistant.ui.map.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * Stateless диалог ошибки карты.
 */
@Composable
fun MapErrorDialog(
    error: String?,
    onDismiss: () -> Unit
) {
    error?.let { errorMsg ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Ошибка") },
            text = { Text(errorMsg) },
            confirmButton = {
                Button(onClick = onDismiss) {
                    Text("OK")
                }
            }
        )
    }
}