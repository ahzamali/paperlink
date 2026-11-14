package com.paperink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMerge
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paperink.data.local.ExperimentalFeatures
import com.paperink.ui.theme.OceanBlue

@Composable
fun WelcomeOnboardingDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isAiEnabled by remember {
        mutableStateOf(ExperimentalFeatures.isAiAssistantEnabled(context))
    }
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = {
            ExperimentalFeatures.setFirstRunCompleted(context, true)
            ExperimentalFeatures.setAiAssistantEnabled(context, isAiEnabled)
            onDismiss()
        },
        modifier = modifier
            .fillMaxWidth()
            .testTag("welcome_onboarding_dialog"),
        shape = RoundedCornerShape(24.dp),
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "PaperInk PDF",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Welcome to PaperInk PDF",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Fast, lightweight, and built for privacy.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
                    .verticalScroll(scrollState)
            ) {
                HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))

                // Feature 1: 100% Private & On-Device
                FeatureHighlightRow(
                    icon = Icons.Default.Shield,
                    iconTint = Color(0xFF16A34A),
                    iconBg = Color(0xFF16A34A).copy(alpha = 0.15f),
                    title = "100% Private & On-Device",
                    description = "Your documents never leave your phone. All PDF rendering, text extraction, password decryption, and OCR run entirely offline on local silicon—zero cloud uploads, zero telemetry, and zero tracking."
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Feature 2: Built-in PDF Utilities (Merge & Compress)
                FeatureHighlightRow(
                    icon = Icons.AutoMirrored.Filled.CallMerge,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    title = "Built-In PDF Utilities: Merge & Compress",
                    description = "Streamline your documents without third-party web tools:\n• Merge PDFs: Combine multiple files into a single unified document with custom page ordering.\n• Smart Compression: Shrink document file sizes by up to 75% for quick sharing while preserving crisp text readability."
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Feature 3: Experimental On-Device AI (Gemma 4 2B)
                FeatureHighlightRow(
                    icon = Icons.Default.AutoAwesome,
                    iconTint = OceanBlue,
                    iconBg = OceanBlue.copy(alpha = 0.15f),
                    title = "Experimental On-Device AI (Gemma 4 2B)",
                    description = "Experience next-generation document intelligence powered by Google's Gemma 4 2B. Summarize chapters, extract action items, and ask questions about your documents locally with complete privacy.\n• Kept hidden by default to keep your reader clean. You can enable it at any time from the 3-dot overflow menu (⋮) in the PDF reader."
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Optional Quick-Toggle Card for AI
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Experimental AI now",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Adds the Gemma 4 2B assistant button to the reader",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAiEnabled,
                            onCheckedChange = { isAiEnabled = it },
                            modifier = Modifier.testTag("onboarding_ai_toggle_switch")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    ExperimentalFeatures.setFirstRunCompleted(context, true)
                    ExperimentalFeatures.setAiAssistantEnabled(context, isAiEnabled)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("onboarding_get_started_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "Get Started",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    )
}

@Composable
private fun FeatureHighlightRow(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = iconBg,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

