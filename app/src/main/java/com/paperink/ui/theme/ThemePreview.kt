package com.paperink.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HeroCardMock(
    paletteName: String,
    containerBg: Color,
    titleColor: Color,
    subtitleColor: Color,
    badgeBg: Color,
    badgeText: Color,
    progressTrack: Color,
    progressFill: Color,
    buttonBg: Color,
    buttonText: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerBg)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "CONTINUE READING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeText,
                    letterSpacing = 0.5.sp
                )
            }
            Text(
                text = paletteName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = subtitleColor
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Quarterly Financial Analysis & Projections.pdf",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = titleColor
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Page 18 of 42 • 3.2 MB • Updated 2 hrs ago",
            fontSize = 13.sp,
            color = subtitleColor
        )

        Spacer(modifier = Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = { 0.43f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
            color = progressFill,
            trackColor = progressTrack
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {},
            colors = ButtonDefaults.buttonColors(
                containerColor = buttonBg,
                contentColor = buttonText
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.align(Alignment.End)
        ) {
            Text("Open Reader", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Preview(name = "Color Palette Comparison (Light Theme)", showBackground = true, backgroundColor = 0xFFF8FAFC)
@Composable
fun ColorPaletteMockPreview() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Current Theme (Red-on-Red Vibration):", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
        HeroCardMock(
            paletteName = "Current Adobe Red",
            containerBg = Color(0xFFFFEBEB),
            titleColor = Color(0xFF990000),
            subtitleColor = Color(0xFFB91C1C),
            badgeBg = Color(0xFFFFD6D6),
            badgeText = Color(0xFF990000),
            progressTrack = Color(0xFFFFD6D6),
            progressFill = Color(0xFFE5252A),
            buttonBg = Color(0xFFE5252A),
            buttonText = Color.White
        )

        Text("Option 1: Cobalt Modern (Recommended - High Contrast):", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
        HeroCardMock(
            paletteName = "Option 1: Cobalt Modern",
            containerBg = Color(0xFFEFF6FF), // Soft Ice Blue
            titleColor = Color(0xFF1E3A8A),  // Deep Navy Ink (AAA contrast)
            subtitleColor = Color(0xFF1D4ED8), // Rich Cobalt
            badgeBg = Color(0xFFDBEAFE),
            badgeText = Color(0xFF1E3A8A),
            progressTrack = Color(0xFFDBEAFE),
            progressFill = Color(0xFF2563EB),
            buttonBg = Color(0xFF2563EB),
            buttonText = Color.White
        )

        Text("Option 2: Nordic Ocean (Calm & Balanced):", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
        HeroCardMock(
            paletteName = "Option 2: Nordic Ocean",
            containerBg = Color(0xFFE0F2FE), // Arctic Sky
            titleColor = Color(0xFF0C4A6E),  // Deep Abyssal Blue
            subtitleColor = Color(0xFF0369A1), // Marine Slate
            badgeBg = Color(0xFFBAE6FD),
            badgeText = Color(0xFF0C4A6E),
            progressTrack = Color(0xFFBAE6FD),
            progressFill = Color(0xFF0284C7),
            buttonBg = Color(0xFF0284C7),
            buttonText = Color.White
        )

        Text("Option 3: Royal Sapphire (Executive & Formal):", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF334155))
        HeroCardMock(
            paletteName = "Option 3: Royal Sapphire",
            containerBg = Color(0xFFEBF3FF),
            titleColor = Color(0xFF0747A6),
            subtitleColor = Color(0xFF172B4D),
            badgeBg = Color(0xFFDEEBFF),
            badgeText = Color(0xFF0747A6),
            progressTrack = Color(0xFFDEEBFF),
            progressFill = Color(0xFF0052CC),
            buttonBg = Color(0xFF0052CC),
            buttonText = Color.White
        )
    }
}
