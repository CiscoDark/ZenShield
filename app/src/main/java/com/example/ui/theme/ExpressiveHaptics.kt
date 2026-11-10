package com.example.ui.theme

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ExpressiveHaptics
 *
 * Physics-correlated haptic feedback engine that synchronizes Android VibrationEffect
 * waveforms with Material 3 Expressive bouncy spring animations (Spring.DampingRatioLowBouncy
 * and Spring.DampingRatioMediumBouncy).
 */
object ExpressiveHaptics {

    enum class HapticPattern(
        val id: String,
        val timings: LongArray,
        val amplitudes: IntArray
    ) {
        /**
         * Correlates with LowBouncy spatial spring expansion when engaging Focus Shield or switching modes:
         * Primary engagement impact (230) -> 1st spring overshoot rebound (145) -> 2nd settling oscillation (75).
         */
        FOCUS_ACTIVATED(
            id = "focus_activated_spring",
            timings = longArrayOf(0L, 24L, 48L, 16L, 64L, 10L),
            amplitudes = intArrayOf(0, 235, 0, 150, 0, 80)
        ),

        /**
         * Correlates with spring release when deactivating Focus Shield:
         * Crisp release pulse (190) -> soft settling tail (85).
         */
        FOCUS_DEACTIVATED(
            id = "focus_deactivated_spring",
            timings = longArrayOf(0L, 20L, 52L, 14L),
            amplitudes = intArrayOf(0, 190, 0, 85)
        ),

        /**
         * Correlates with notification dismissal, call rejection, or clearing intercept logs:
         * Snappy double-tick waveform confirming distraction suppression.
         */
        NOTIFICATION_DISMISSED(
            id = "notification_dismissed_spring",
            timings = longArrayOf(0L, 12L, 34L, 18L),
            amplitudes = intArrayOf(0, 175, 0, 225)
        ),

        /**
         * Correlates with tactile press-squish and corner morphing on Bento tiles, chips, and slider detents.
         */
        SPRING_TAP(
            id = "spring_tap_micro",
            timings = longArrayOf(0L, 10L, 28L, 6L),
            amplitudes = intArrayOf(0, 145, 0, 65)
        )
    }

    private val _lastPlayedPattern = MutableStateFlow<HapticPattern?>(null)
    val lastPlayedPattern: StateFlow<HapticPattern?> = _lastPlayedPattern.asStateFlow()

    private val _triggerCount = MutableStateFlow(0)
    val triggerCount: StateFlow<Int> = _triggerCount.asStateFlow()

    fun playFocusActivated(context: Context) {
        dispatchWaveform(context, HapticPattern.FOCUS_ACTIVATED)
    }

    fun playFocusDeactivated(context: Context) {
        dispatchWaveform(context, HapticPattern.FOCUS_DEACTIVATED)
    }

    fun playNotificationDismissed(context: Context) {
        dispatchWaveform(context, HapticPattern.NOTIFICATION_DISMISSED)
    }

    fun playSpringTap(context: Context) {
        dispatchWaveform(context, HapticPattern.SPRING_TAP)
    }

    private fun dispatchWaveform(context: Context, pattern: HapticPattern) {
        _lastPlayedPattern.value = pattern
        _triggerCount.value += 1

        // Cinema Mode Zero-Vibration Guardrail: mute physical vibration motor in movie theaters
        val suppressMotor = runCatching {
            com.example.core.FocusStateManager.isCinemaZeroVibrationActive(context)
        }.getOrDefault(false)
        if (suppressMotor) return

        runCatching {
            val vibrator = resolveVibrator(context) ?: return
            if (!vibrator.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = if (vibrator.hasAmplitudeControl()) {
                    VibrationEffect.createWaveform(pattern.timings, pattern.amplitudes, -1)
                } else {
                    VibrationEffect.createWaveform(pattern.timings, -1)
                }
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern.timings, -1)
            }
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
