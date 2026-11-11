package de.mm20.launcher2.ui.component

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

enum class AnimationPhysicsPreset(val label: String, val description: String) {
    HyperFast("Hyper-Fast Cyber", "150ms instantaneous transitions"),
    SmoothFluid("Smooth Fluid Spring", "Bouncy, natural spring motion"),
    TacticalSnap("Tactical Military Snap", "Sharp, immediate response")
}

object TelosAnimationPhysics {

    fun <T> getSpec(preset: AnimationPhysicsPreset): AnimationSpec<T> = when (preset) {
        AnimationPhysicsPreset.HyperFast -> tween(durationMillis = 150, easing = FastOutLinearInEasing)
        AnimationPhysicsPreset.SmoothFluid -> spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
        AnimationPhysicsPreset.TacticalSnap -> tween(durationMillis = 80, easing = LinearEasing)
    }
}
