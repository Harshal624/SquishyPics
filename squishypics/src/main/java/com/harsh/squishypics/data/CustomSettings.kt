package com.harsh.squishypics.data

import androidx.annotation.IntRange

data class CustomSettings(
    @IntRange(from = 1, to = Long.MAX_VALUE) val targetWidth: Int,
    @IntRange(from = 1, to = Long.MAX_VALUE) val targetHeight: Int,
    @IntRange(from = 0, to = 100) val targetQuality: Int
)