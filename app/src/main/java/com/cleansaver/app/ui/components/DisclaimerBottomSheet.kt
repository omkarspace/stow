package com.cleansaver.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisclaimerBottomSheet(
    sheetState: SheetState,
    onAccept: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = { /* Require explicit accept */ },
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Welcome to CleanSaver",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "CleanSaver is an independent, utility-first media saver for Android built on strict privacy principles:",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "• 100% On-Device: All requests run directly from your phone to public CDNs. No proxy or backend servers.\n" +
                       "• Zero Analytics & Ads: No SDKs, no tracking, no logins, no personal data collection.\n" +
                       "• Independent & Fair Use: CleanSaver is unaffiliated with Meta or Instagram. Users are responsible for respecting creator rights and fair-use guidelines.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I Understand & Agree")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

