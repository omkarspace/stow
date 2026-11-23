package com.cleansaver.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cleansaver.app.ui.theme.StowInkMutedOnDark
import com.cleansaver.app.ui.theme.StowInkOnDark
import com.cleansaver.app.ui.theme.StowLavender
import com.cleansaver.app.ui.theme.StowPineDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisclaimerBottomSheet(
    sheetState: SheetState,
    onAccept: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = { /* Require explicit accept */ },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = StowPineDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "STOW®", style = MaterialTheme.typography.headlineMedium, color = StowInkOnDark)
                StowLavenderBadge(text = "ON-DEVICE")
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Save public media cleanly, with zero clutter.",
                style = MaterialTheme.typography.titleLarge,
                color = StowInkOnDark
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "• 100% on-device — no backend servers or proxies.\n" +
                    "• Zero ads, zero logins, and zero analytics.\n" +
                    "• Independent utility unaffiliated with Meta, X, Reddit, Pinterest, TikTok, or YouTube. Respect creator rights.",
                style = MaterialTheme.typography.bodySmall,
                color = StowInkMutedOnDark
            )

            Spacer(modifier = Modifier.height(22.dp))

            Button(
                onClick = onAccept,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = StowLavender,
                    contentColor = StowPineDark
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text("CONTINUE", style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
