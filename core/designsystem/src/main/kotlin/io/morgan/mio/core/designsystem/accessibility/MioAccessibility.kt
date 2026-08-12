package io.morgan.mio.core.designsystem.accessibility

import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Immutable
public data class MioAccessibility(
    public val highContrastEnabled: Boolean,
    public val reducedMotionEnabled: Boolean,
    public val hapticsEnabled: Boolean,
) {
    public val spatialMotionEnabled: Boolean
        get() = !reducedMotionEnabled
}

@Composable
public fun rememberSystemReducedMotionEnabled(): Boolean {
    val context = LocalContext.current.applicationContext
    var reducedMotionEnabled by remember(context) {
        mutableStateOf(isReducedMotionScale(readAnimatorDurationScale(context)))
    }

    DisposableEffect(context) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    reducedMotionEnabled = isReducedMotionScale(readAnimatorDurationScale(context))
                }
            }
        val resolver = context.contentResolver
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        reducedMotionEnabled = isReducedMotionScale(readAnimatorDurationScale(context))
        onDispose { resolver.unregisterContentObserver(observer) }
    }

    return reducedMotionEnabled
}

@Composable
public fun rememberSystemHighContrastEnabled(): Boolean {
    val context = LocalContext.current
    val accessibilityManager =
        remember(context) {
            context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        }
    var highContrastEnabled by remember(accessibilityManager) {
        mutableStateOf(readHighContrastState(accessibilityManager))
    }

    DisposableEffect(accessibilityManager) {
        if (Build.VERSION.SDK_INT < 36 || accessibilityManager == null) {
            onDispose { }
        } else {
            val listener =
                AccessibilityManager.HighContrastTextStateChangeListener { enabled ->
                    highContrastEnabled = enabled
                }
            accessibilityManager.addHighContrastTextStateChangeListener(context.mainExecutor, listener)
            highContrastEnabled = accessibilityManager.isHighContrastTextEnabled
            onDispose { accessibilityManager.removeHighContrastTextStateChangeListener(listener) }
        }
    }

    return highContrastEnabled
}

private fun readAnimatorDurationScale(context: Context): Float =
    runCatching {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
        )
    }.getOrDefault(1f)

internal fun isReducedMotionScale(scale: Float): Boolean = scale == 0f

private fun readHighContrastState(accessibilityManager: AccessibilityManager?): Boolean =
    if (Build.VERSION.SDK_INT >= 36) {
        accessibilityManager?.isHighContrastTextEnabled == true
    } else {
        false
    }
