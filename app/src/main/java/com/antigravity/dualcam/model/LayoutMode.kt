package com.antigravity.dualcam.model

/**
 * Camera compositing layouts for single and dual camera recording.
 */
enum class LayoutMode(val displayName: String) {
    SINGLE_REAR("Single Rear"),
    SINGLE_FRONT("Single Front"),
    PIP_FRONT_ON_REAR("PiP (Front on Rear)"),
    PIP_REAR_ON_FRONT("PiP (Rear on Front)"),
    SPLIT_HORIZONTAL("Side-by-Side (Horizontal)"),
    SPLIT_VERTICAL("Top-Bottom (Vertical)"),
    CINEMATIC_SPLIT("Cinematic 70/30 Split");

    val isDual: Boolean
        get() = this != SINGLE_REAR && this != SINGLE_FRONT

    val isPiP: Boolean
        get() = this == PIP_FRONT_ON_REAR || this == PIP_REAR_ON_FRONT

    val isSplit: Boolean
        get() = this == SPLIT_HORIZONTAL || this == SPLIT_VERTICAL || this == CINEMATIC_SPLIT
}
