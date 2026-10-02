package com.example.wordexplainer

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout

/**
 * Minimal transparent Activity whose ONLY job is to open the keyboard and
 * let the user type a question. On submit it calls ChatBridge.onMessageSent
 * and finishes with no animation — so from the user's perspective it feels
 * like the keyboard just popped up for the overlay card's input row.
 */
class ChatInputActivity : Activity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = (v * resources.displayMetrics.density)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor  = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
        )
        // No dim behind this "window"
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)

        // Transparent full-screen root — tapping outside the input bar dismisses
        val root = RelativeLayout(this).apply {
            setBackgroundColor(0x55000000.toInt())
            setOnClickListener { dismissNoAnim() }
        }

        // Input row — styled exactly like the card's input row
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity     = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(6), dp(6))
            background = GradientDrawable().apply {
                setColor(0xFF0E0F14.toInt())
                cornerRadius = dp(24f)
            }
            // Prevent tap-through to the root dismiss handler
            setOnClickListener { /* consume */ }
        }

        val inputEt = EditText(this).apply {
            hint      = "Ask anything about this..."
            setHintTextColor(0x55FFFFFF.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            typeface  = OutfitFonts.regular(this@ChatInputActivity)
            textSize  = 14f
            background = null
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines  = 4
            requestFocus()
        }
        inputRow.addView(inputEt, LinearLayout.LayoutParams(0, -2, 1f))

        val sendBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_send)
            imageTintList = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                setColor(0xFF1E2235.toInt())
                cornerRadius = dp(18f)
            }
            setOnClickListener {
                val q = inputEt.text.toString().trim()
                if (q.isNotEmpty()) {
                    ChatBridge.onMessageSent?.invoke(q)
                }
                dismissNoAnim()
            }
        }
        inputRow.addView(sendBtn, LinearLayout.LayoutParams(dp(38), dp(38)))

        val rowLp = RelativeLayout.LayoutParams(-1, -2).apply {
            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            setMargins(dp(10), 0, dp(10), dp(12))
        }
        root.addView(inputRow, rowLp)
        setContentView(root)
    }

    private fun dismissNoAnim() {
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onBackPressed() { dismissNoAnim() }
}
