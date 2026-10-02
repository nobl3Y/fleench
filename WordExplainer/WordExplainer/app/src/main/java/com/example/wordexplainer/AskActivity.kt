package com.example.wordexplainer

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.*

class AskActivity : Activity() {

    private lateinit var prefs: Prefs
    private val history = mutableListOf<Pair<String, String>>()
    private lateinit var bodyLL: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var inputEt: EditText
    private var surroundingContext = ""

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = (v * resources.displayMetrics.density)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.BLACK
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        val selectedText = intent.getStringExtra("selectedText") ?: ""
        val contextBlock = intent.getStringExtra("contextBlock") ?: ""
        surroundingContext = intent.getStringExtra("surroundingContext") ?: contextBlock

        // Fullscreen container: clicking the upper backdrop dismisses cleanly
        val root = RelativeLayout(this).apply {
            setBackgroundColor(0x77000000.toInt()) // subtle dim
            setOnClickListener { dismissWithAnim() }
        }

        // AMOLED Bottom Sheet Card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(16))
            background = GradientDrawable().apply {
                setColor(0xFF07080B.toInt())
                cornerRadii = floatArrayOf(
                    dp(24f), dp(24f), dp(24f), dp(24f),
                    0f, 0f, 0f, 0f
                )
            }
            elevation = dp(24f)
            // Clicking inside the card prevents dismissing
            setOnClickListener { /* consume */ }
        }

        // ── Drag Handle ───────────────────────────────────────────────────────
        val handleArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(6), dp(16), dp(14))
        }
        val handle = View(this).apply {
            background = GradientDrawable().apply {
                setColor(0xFF333848.toInt())
                cornerRadius = dp(2f)
            }
        }
        handleArea.addView(handle, LinearLayout.LayoutParams(dp(44), dp(4)))
        card.addView(handleArea)

        // Drag handle to dismiss
        var downY = 0f
        handleArea.setOnTouchListener { _, me ->
            when (me.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downY = me.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    val dy = me.rawY - downY
                    if (dy > 0) card.translationY = dy
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (card.translationY > dp(50f)) {
                        dismissWithAnim()
                    } else {
                        card.animate().translationY(0f).setDuration(160).start()
                    }
                    true
                }
                else -> true
            }
        }

        // ── Header ────────────────────────────────────────────────────────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleTv = TextView(this).apply {
            text = "Ask AI"
            textSize = 17f
            typeface = OutfitFonts.bold(this@AskActivity)
            setTextColor(0xFFFFFFFF.toInt())
        }
        header.addView(titleTv)

        if (selectedText.isNotBlank()) {
            val pillTv = TextView(this).apply {
                text = "\"${selectedText.take(24)}${if (selectedText.length > 24) "…" else ""}\""
                textSize = 12f
                typeface = OutfitFonts.medium(this@AskActivity)
                setTextColor(0xFF93C5FD.toInt())
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = GradientDrawable().apply {
                    setColor(0x2238BDF8.toInt())
                    cornerRadius = dp(8f)
                }
            }
            header.addView(pillTv, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(10) })
        }

        val spacer = View(this)
        header.addView(spacer, LinearLayout.LayoutParams(0, 0, 1f))

        val closeBtn = ImageView(this).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = android.content.res.ColorStateList.valueOf(0xCCFFFFFF.toInt())
            setPadding(dp(7), dp(7), dp(7), dp(7))
            background = GradientDrawable().apply {
                setColor(0x18FFFFFF.toInt())
                cornerRadius = dp(12f)
            }
            setOnClickListener { dismissWithAnim() }
        }
        header.addView(closeBtn, LinearLayout.LayoutParams(dp(34), dp(34)))
        card.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        // ── Chat Messages Scroll Area ─────────────────────────────────────────
        bodyLL = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply { addView(bodyLL) }
        card.addView(scroll, LinearLayout.LayoutParams(-1, dp(190)).apply { bottomMargin = dp(10) })

        // ── Bottom Input Row ──────────────────────────────────────────────────
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                setColor(0x18FFFFFF.toInt())
                cornerRadius = dp(20f)
            }
        }

        inputEt = EditText(this).apply {
            hint = "Ask anything about this..."
            setHintTextColor(0x66FFFFFF.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            typeface = OutfitFonts.regular(this@AskActivity)
            textSize = 14f
            background = null
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
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
                    inputEt.setText("")
                    sendMessage(q)
                }
            }
        }
        inputRow.addView(sendBtn, LinearLayout.LayoutParams(dp(36), dp(36)))
        card.addView(inputRow, LinearLayout.LayoutParams(-1, -2))

        val cardLp = RelativeLayout.LayoutParams(-1, -2).apply {
            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        }
        root.addView(card, cardLp)
        setContentView(root)

        // Slide in from bottom smoothly
        card.translationY = dp(280f)
        card.animate().translationY(0f).setDuration(220).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun dismissWithAnim() {
        val root = window.decorView as? ViewGroup
        val card = root?.getChildAt(0) as? ViewGroup
        if (card != null) {
            card.animate()
                .translationY(dp(350f))
                .alpha(0f)
                .setDuration(180)
                .withEndAction {
                    finish()
                    overridePendingTransition(0, 0)
                }
                .start()
        } else {
            finish()
            overridePendingTransition(0, 0)
        }
    }

    override fun onBackPressed() {
        dismissWithAnim()
    }

    private fun addBubble(role: String, msg: String) {
        val isUser = role == "user"
        val bv = TextView(this).apply {
            text = if (isUser) msg else fmtMarkdown(msg)
            textSize = 13.5f
            typeface = OutfitFonts.regular(this@AskActivity)
            setTextColor(0xFFE2E8F0.toInt())
            setLineSpacing(dp(2f), 1.1f)
            setTextIsSelectable(true)
            setPadding(dp(11), dp(8), dp(11), dp(8))
            background = GradientDrawable().apply {
                setColor(if (isUser) 0xFF1E293B.toInt() else 0x14FFFFFF.toInt())
                cornerRadius = dp(12f)
            }
        }
        val lp = LinearLayout.LayoutParams(-2, -2).apply {
            gravity = if (isUser) Gravity.END else Gravity.START
            topMargin = dp(6)
            marginStart = if (isUser) dp(36) else 0
            marginEnd = if (isUser) 0 else dp(36)
        }
        bodyLL.addView(bv, lp)
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun createShimmer(tv: TextView): ValueAnimator {
        val baseColor = 0xFF475569.toInt()
        val highlightColor = 0xFFFFFFFF.toInt()
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = android.view.animation.LinearInterpolator()
        }
        tv.post {
            val tw = tv.paint.measureText(tv.text.toString().ifEmpty { "Thinking..." })
            if (tw <= 0f) return@post
            val gradW = tw * 0.7f
            val shader = LinearGradient(
                0f, 0f, gradW, 0f,
                intArrayOf(baseColor, highlightColor, baseColor),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            tv.paint.shader = shader
            val matrix = Matrix()
            anim.addUpdateListener { va ->
                val fraction = va.animatedValue as Float
                val tx = -gradW + fraction * (tw + 2f * gradW)
                matrix.setTranslate(tx, 0f)
                shader.setLocalMatrix(matrix)
                tv.invalidate()
            }
            if (tv.text.startsWith("Thinking")) {
                anim.start()
            }
        }
        return anim
    }

    private fun sendMessage(q: String) {
        history.add("user" to q)
        addBubble("user", q)

        val thinking = TextView(this).apply {
            text = "Thinking…"
            textSize = 12f
            typeface = OutfitFonts.regular(this@AskActivity)
            setTextColor(0x66FFFFFF.toInt())
            setPadding(dp(4), dp(2), dp(4), dp(2))
        }
        bodyLL.addView(thinking)
        val shimmer = createShimmer(thinking)

        Thread {
            val sysPrompt = buildString {
                if (prefs.name.isNotBlank()) append("User's name is ${prefs.name}. ")
                append("You are a friendly, concise reading companion. Answer the user's questions directly. ")
                if (surroundingContext.isNotBlank()) {
                    append("\nScreen / conversation context:\n\"\"\"\n${surroundingContext.take(2500)}\n\"\"\"\n")
                }
            }
            val reply = try {
                Ai.chat(prefs.key, sysPrompt, history)
            } catch (e: Exception) {
                "Could not reach AI: ${e.message}"
            }

            runOnUiThread {
                shimmer.cancel()
                thinking.paint.shader = null
                bodyLL.removeView(thinking)
                history.add("assistant" to reply)
                addBubble("assistant", reply)
            }
        }.start()
    }

    private fun fmtMarkdown(text: String): CharSequence {
        val sb = SpannableStringBuilder()
        val parts = text.split("**")
        parts.forEachIndexed { i, part ->
            val s = sb.length
            sb.append(part)
            if (i % 2 == 1) {
                sb.setSpan(StyleSpan(Typeface.BOLD), s, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(ForegroundColorSpan(0xFFF1F5F9.toInt()), s, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return sb
    }
}
