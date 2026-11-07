package com.paperink.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paperink.ui.theme.AnnotationBlack
import com.paperink.ui.theme.AnnotationBlue
import com.paperink.ui.theme.AnnotationGreen
import com.paperink.ui.theme.AnnotationOrange
import com.paperink.ui.theme.AnnotationPink
import com.paperink.ui.theme.AnnotationPurple
import com.paperink.ui.theme.AnnotationRed
import com.paperink.ui.theme.AnnotationYellow

val PenInkColors = listOf(
    AnnotationBlack,           // Classic Ink Black
    Color(0xFF2563EB),        // Royal Blue
    Color(0xFFDC2626),        // Crimson Red
    Color(0xFF16A34A),        // Forest Green
    Color(0xFF9333EA),        // Royal Purple
    Color(0xFFD97706),        // Amber Orange
    Color(0xFF64748B),        // Slate Grey
    Color(0xFFFFFFFF)         // White Correction Ink
)

val HighlighterInkColors = listOf(
    AnnotationYellow,         // Fluorescent Yellow
    AnnotationGreen,          // Mint Green
    AnnotationBlue,           // Sky Blue
    AnnotationPink,           // Coral Pink
    AnnotationOrange          // Warm Orange
)

@Composable
fun ColorPickerRow(
    selectedColor: Color,
    onColorSelected: (Color) -> Unit,
    strokeWidth: Float,
    onStrokeWidthChange: (Float) -> Unit,
    isHighlighter: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = if (isHighlighter) HighlighterInkColors else PenInkColors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("color_picker_panel"),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isHighlighter) "Highlighter Ink Color:" else "Pen Ink Color:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${strokeWidth.toInt()} dp",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Color circles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                palette.forEach { color ->
                    val isSelected = color.toArgb() == selectedColor.toArgb()
                    val isLightColor = color == Color.White || color == AnnotationYellow || color == AnnotationGreen
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.25f),
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(color) }
                            .testTag("color_item_${color.toArgb()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected Ink Color",
                                tint = if (isLightColor) Color.Black else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Stroke Thickness Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isHighlighter) "Highlight Width" else "Pen Tip Width",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(110.dp)
                )

                Slider(
                    value = strokeWidth,
                    onValueChange = onStrokeWidthChange,
                    valueRange = if (isHighlighter) 10f..40f else 2f..24f,
                    steps = 8,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stroke_width_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Preview stroke dot
                Box(
                    modifier = Modifier.size(30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size((strokeWidth.coerceIn(4f, 22f)).dp)
                            .clip(CircleShape)
                            .background(selectedColor.copy(alpha = if (isHighlighter) 0.5f else 1f))
                            .border(
                                width = if (selectedColor == Color.White) 1.dp else 0.dp,
                                color = if (selectedColor == Color.White) Color.Gray else Color.Transparent,
                                shape = CircleShape
                            )
                    )
                }
            }
        }
    }
}
