package com.harsh.squishypics.data

enum class CompressQuality(val id: Int, val targetQuality: Int, val targetMaxDimensionMultiplier: Float) {
    LOW(1, 60, 0.4F), MEDIUM(2, 70, 0.5F), HIGH(3, 80, 0.75F)
}