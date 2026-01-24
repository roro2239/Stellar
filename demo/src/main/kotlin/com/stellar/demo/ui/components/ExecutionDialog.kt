package com.stellar.demo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ExecutionDialog(
    title: String,
    output: String,
    isRunning: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isRunning) onDismiss() },
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = title)
                if (isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        },
        text = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium
            ) {
                val scrollState = rememberScrollState()
                Text(
                    text = output.ifEmpty {
                        if (isRunning) "执行中..." else "等待执行..."
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (output.isEmpty())
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isRunning
            ) {
                Text(if (isRunning) "执行中..." else "关闭")
            }
        }
    )
}
