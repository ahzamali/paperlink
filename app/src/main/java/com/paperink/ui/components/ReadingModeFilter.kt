package com.paperink.ui.components

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.paperink.data.model.ReadingMode

object ReadingModeFilters {

    val InvertedNightMatrix: ColorMatrix by lazy {
        ColorMatrix(
            floatArrayOf(
                -0.85f,  0.00f,  0.00f, 0f, 220f,
                 0.00f, -0.85f,  0.00f, 0f, 220f,
                 0.00f,  0.00f, -0.85f, 0f, 230f,
                 0.00f,  0.00f,  0.00f, 1f,   0f
            )
        )
    }

    val SepiaMatrix: ColorMatrix by lazy {
        ColorMatrix(
            floatArrayOf(
                0.393f * 1.05f, 0.769f * 1.05f, 0.189f * 1.05f, 0f, 15f,
                0.349f * 0.98f, 0.686f * 0.98f, 0.168f * 0.98f, 0f, 10f,
                0.272f * 0.85f, 0.534f * 0.85f, 0.131f * 0.85f, 0f, -5f,
                0.000f,         0.000f,         0.000f,         1f,  0f
            )
        )
    }

    fun getColorFilter(mode: ReadingMode): ColorFilter? {
        return when (mode) {
            ReadingMode.NORMAL -> null
            ReadingMode.NIGHT -> ColorFilter.colorMatrix(InvertedNightMatrix)
            ReadingMode.SEPIA -> ColorFilter.colorMatrix(SepiaMatrix)
        }
    }
}
