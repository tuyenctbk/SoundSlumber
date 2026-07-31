package com.example.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SuggestionDialog(
    type: SuggestionType,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val title = when (type) {
        SuggestionType.SHARE -> "Share with Friends"
        SuggestionType.RATE -> "Rate 5 Stars"
        SuggestionType.UPDATE -> "New Update Available"
    }
    val message = when (type) {
        SuggestionType.SHARE -> "Enjoying SoundSlumber? Share it with your friends!"
        SuggestionType.RATE -> "If you like SoundSlumber, please rate us 5 stars!"
        SuggestionType.UPDATE -> "A new version of SoundSlumber is available. Update now!"
    }
    val confirmText = when (type) {
        SuggestionType.UPDATE -> "Update Now"
        else -> "Sure!"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = { Text(text = message) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = confirmText)
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(text = "Later")
            }
        }
    )
}
