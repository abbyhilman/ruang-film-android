package com.assessment.movies.ui.components

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

internal class TrailerFullscreenHost {
    private var dialog: Dialog? = null
    private var content: View? = null
    private var callback: WebChromeClient.CustomViewCallback? = null

    @Suppress("DEPRECATION")
    fun show(context: Context, view: View, onHidden: WebChromeClient.CustomViewCallback) {
        val activity = context.activity()
        if (dialog != null || view.parent != null || activity == null || activity.isFinishing || activity.isDestroyed) {
            onHidden.onCustomViewHidden()
            return
        }
        val container = FrameLayout(activity).apply {
            setBackgroundColor(Color.BLACK)
            addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val fullscreen = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
            setContentView(container)
            setCanceledOnTouchOutside(false)
            setOnDismissListener { this@TrailerFullscreenHost.hide() }
        }
        content = view
        callback = onHidden
        dialog = fullscreen
        fullscreen.show()
        fullscreen.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        view.requestFocus()
    }

    fun hide() {
        val fullscreen = dialog ?: return
        val onHidden = callback
        val view = content
        dialog = null
        callback = null
        content = null
        (view?.parent as? ViewGroup)?.removeView(view)
        fullscreen.setOnDismissListener(null)
        fullscreen.dismiss()
        onHidden?.onCustomViewHidden()
    }
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.takeUnless { it === this }?.activity()
    else -> null
}
