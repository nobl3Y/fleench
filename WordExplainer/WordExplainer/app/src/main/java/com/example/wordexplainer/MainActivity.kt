package com.example.wordexplainer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var statusDot: View
    private lateinit var statusLabel: TextView
    private lateinit var statusSub: TextView
    private lateinit var sizeValueTv: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun dp(v: Float) = v * resources.displayMetrics.density

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)

        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        prefs = Prefs(this)
        Thread { OfflineDictionary.init(this) }.start()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(22), dp(44), dp(22), dp(48))
        }

        // ── App Header ────────────────────────────────────────────────────────
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logo = ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply {
                setColor(0xFF10121A.toInt())
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), 0xFF222638.toInt())
            }
            setPadding(dp(4), dp(4), dp(4), dp(4))
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)).apply { marginEnd = dp(16) })

        val titleCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val appName = TextView(this).apply {
            text = "Fleench"
            textSize = 28f
            typeface = OutfitFonts.bold(this@MainActivity)
            setTextColor(0xFFFFFFFF.toInt())
            letterSpacing = -0.01f
        }
        val tagline = TextView(this).apply {
            text = "Screen Reading Companion"
            textSize = 13.5f
            typeface = OutfitFonts.regular(this@MainActivity)
            setTextColor(0xFF64748B.toInt())
        }
        titleCol.addView(appName)
        titleCol.addView(tagline)
        header.addView(titleCol, LinearLayout.LayoutParams(0, -2, 1f))

        root.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(32) })

        fun addSectionHeader(title: String) {
            val tv = TextView(this).apply {
                text = title
                textSize = 11.5f
                typeface = OutfitFonts.semibold(this@MainActivity)
                setTextColor(0xFF718096.toInt())
                letterSpacing = 0.08f
            }
            root.addView(tv, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(18)
                bottomMargin = dp(8)
            })
        }

        fun createCard(): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(16), dp(18), dp(16))
                background = GradientDrawable().apply {
                    setColor(0xFF0C0D13.toInt())
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), 0xFF1C1F2B.toInt())
                }
            }
        }

        // ═════════════════════════════════════════════════════════════════════
        //  SECTION 1: SERVICE & ACCESSIBILITY
        // ═════════════════════════════════════════════════════════════════════
        addSectionHeader("SERVICE & STATUS")

        val statusCard = createCard()
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        statusDot = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF333333.toInt())
            }
        }
        statusRow.addView(statusDot, LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(14) })

        val statusText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        statusLabel = TextView(this).apply {
            text = "Service inactive"
            textSize = 15f
            typeface = OutfitFonts.semibold(this@MainActivity)
            setTextColor(0xFFFFFFFF.toInt())
        }
        statusSub = TextView(this).apply {
            text = "Tap below to enable in Accessibility settings"
            textSize = 12.5f
            typeface = OutfitFonts.regular(this@MainActivity)
            setTextColor(0xFF64748B.toInt())
        }
        statusText.addView(statusLabel)
        statusText.addView(statusSub)
        statusRow.addView(statusText, LinearLayout.LayoutParams(0, -2, 1f))
        statusCard.addView(statusRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val settingsBtn = TextView(this).apply {
            text = "Open Accessibility Settings"
            textSize = 14f
            typeface = OutfitFonts.medium(this@MainActivity)
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            background = GradientDrawable().apply {
                setColor(0xFF161A26.toInt())
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), 0xFF2A2F45.toInt())
            }
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        statusCard.addView(settingsBtn, LinearLayout.LayoutParams(-1, -2))
        root.addView(statusCard)

        // ═════════════════════════════════════════════════════════════════════
        //  SECTION 2: BUBBLE & POSITION (COLLAPSIBLE ACCORDION)
        // ═════════════════════════════════════════════════════════════════════
        addSectionHeader("BUBBLE & POSITION")

        val accordionCard = createCard()

        val accordionHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
        }

        val headerTextCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val headerTitle = TextView(this).apply {
            text = "Bubble Customization"
            textSize = 14.5f
            typeface = OutfitFonts.semibold(this@MainActivity)
            setTextColor(0xFFFFFFFF.toInt())
        }
        val headerSubtitle = TextView(this).apply {
            text = "Size, opacity, bezel peek & position"
            textSize = 12f
            typeface = OutfitFonts.regular(this@MainActivity)
            setTextColor(0xFF64748B.toInt())
        }
        headerTextCol.addView(headerTitle)
        headerTextCol.addView(headerSubtitle)
        accordionHeader.addView(headerTextCol, LinearLayout.LayoutParams(0, -2, 1f))

        val chevron = ImageView(this).apply {
            setImageResource(R.drawable.ic_chevron_down)
            imageTintList = android.content.res.ColorStateList.valueOf(0xFF94A3B8.toInt())
            rotation = 0f // Initially closed
        }
        accordionHeader.addView(chevron, LinearLayout.LayoutParams(dp(22), dp(22)))
        accordionCard.addView(accordionHeader, LinearLayout.LayoutParams(-1, -2))

        val accordionBody = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE // Initially closed
        }

        val accordionDivider = View(this).apply {
            setBackgroundColor(0x18FFFFFF.toInt())
        }
        accordionBody.addView(accordionDivider, LinearLayout.LayoutParams(-1, dp(1)).apply {
            topMargin = dp(14)
            bottomMargin = dp(14)
        })

        val sliderTrackTint = android.content.res.ColorStateList.valueOf(0xFF475569.toInt())
        val sliderThumbTint = android.content.res.ColorStateList.valueOf(0xFF94A3B8.toInt())

        // 1. Diameter Slider
        val sizeLabelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val sizeTitle = TextView(this).apply {
            text = "Diameter"
            textSize = 14f
            typeface = OutfitFonts.medium(this@MainActivity)
            setTextColor(0xFFCBD5E1.toInt())
        }
        sizeLabelRow.addView(sizeTitle, LinearLayout.LayoutParams(0, -2, 1f))

        sizeValueTv = TextView(this).apply {
            text = "${prefs.circleSizeDp} dp"
            textSize = 14f
            typeface = OutfitFonts.bold(this@MainActivity)
            setTextColor(0xFFCBD5E1.toInt())
        }
        sizeLabelRow.addView(sizeValueTv)
        accordionBody.addView(sizeLabelRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val sizeSlider = SeekBar(this).apply {
            max = 80 - 36 // Range: 36 to 80 dp
            progress = prefs.circleSizeDp - 36
            progressTintList = sliderTrackTint
            thumbTintList = sliderThumbTint
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val sizeDp = p + 36
                    prefs.circleSizeDp = sizeDp
                    sizeValueTv.text = "$sizeDp dp"
                    if (fromUser) {
                        sendBroadcast(Intent("com.example.wordexplainer.UPDATE_SIZE").setPackage(packageName))
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        accordionBody.addView(sizeSlider, LinearLayout.LayoutParams(-1, -2))

        // 2. Opacity Slider
        val alphaLabelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val alphaTitle = TextView(this).apply {
            text = "Opacity"
            textSize = 14f
            typeface = OutfitFonts.medium(this@MainActivity)
            setTextColor(0xFFCBD5E1.toInt())
        }
        alphaLabelRow.addView(alphaTitle, LinearLayout.LayoutParams(0, -2, 1f))

        val alphaValueTv = TextView(this).apply {
            text = "${prefs.circleAlpha}%"
            textSize = 14f
            typeface = OutfitFonts.bold(this@MainActivity)
            setTextColor(0xFFCBD5E1.toInt())
        }
        alphaLabelRow.addView(alphaValueTv)
        accordionBody.addView(alphaLabelRow, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(16)
            bottomMargin = dp(10)
        })

        val alphaSlider = SeekBar(this).apply {
            max = 100 - 20 // Range: 20% to 100%
            progress = prefs.circleAlpha - 20
            progressTintList = sliderTrackTint
            thumbTintList = sliderThumbTint
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val alphaPct = p + 20
                    prefs.circleAlpha = alphaPct
                    alphaValueTv.text = "$alphaPct%"
                    if (fromUser) {
                        sendBroadcast(Intent("com.example.wordexplainer.UPDATE_ALPHA").setPackage(packageName))
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        accordionBody.addView(alphaSlider, LinearLayout.LayoutParams(-1, -2))

        // 3. Bezel Peek Slider
        val peekLabelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val peekTitle = TextView(this).apply {
            text = "Bezel Peek"
            textSize = 14f
            typeface = OutfitFonts.medium(this@MainActivity)
            setTextColor(0xFFCBD5E1.toInt())
        }
        peekLabelRow.addView(peekTitle, LinearLayout.LayoutParams(0, -2, 1f))

        val peekValueTv = TextView(this).apply {
            text = "${prefs.circlePeekDp} dp"
            textSize = 14f
            typeface = OutfitFonts.bold(this@MainActivity)
            setTextColor(0xFFCBD5E1.toInt())
        }
        peekLabelRow.addView(peekValueTv)
        accordionBody.addView(peekLabelRow, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(16)
            bottomMargin = dp(4)
        })

        val peekSub = TextView(this).apply {
            text = "Protrusion width exposed from the screen edge"
            textSize = 12f
            typeface = OutfitFonts.regular(this@MainActivity)
            setTextColor(0xFF64748B.toInt())
        }
        accordionBody.addView(peekSub, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })

        val peekSlider = SeekBar(this).apply {
            max = 45 - 14 // Range: 14 to 45 dp
            progress = prefs.circlePeekDp - 14
            progressTintList = sliderTrackTint
            thumbTintList = sliderThumbTint
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                    val peekDp = p + 14
                    prefs.circlePeekDp = peekDp
                    peekValueTv.text = "$peekDp dp"
                    if (fromUser) {
                        sendBroadcast(Intent("com.example.wordexplainer.UPDATE_PEEK").setPackage(packageName))
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
        }
        accordionBody.addView(peekSlider, LinearLayout.LayoutParams(-1, -2))

        // 4. Resting Position Section
        val posDesc = TextView(this).apply {
            text = "Click below to drag the bubble anywhere on your screen and release it. It will permanently rest there."
            textSize = 12.5f
            typeface = OutfitFonts.regular(this@MainActivity)
            setTextColor(0xFF94A3B8.toInt())
            setLineSpacing(dp(2f), 1.15f)
        }
        accordionBody.addView(posDesc, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(20)
            bottomMargin = dp(12)
        })

        val moveBtn = TextView(this).apply {
            text = "Move Bubble on Screen"
            textSize = 14f
            typeface = OutfitFonts.medium(this@MainActivity)
            setTextColor(0xFFE2E8F0.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            background = GradientDrawable().apply {
                setColor(0xFF161A26.toInt())
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), 0xFF2A2F45.toInt())
            }
            setOnClickListener {
                sendBroadcast(Intent("com.example.wordexplainer.SET_POSITION_MODE").setPackage(packageName))
                Toast.makeText(this@MainActivity, "Now drag the bubble on screen to any spot and release", Toast.LENGTH_LONG).show()
            }
        }
        accordionBody.addView(moveBtn, LinearLayout.LayoutParams(-1, -2))

        accordionCard.addView(accordionBody, LinearLayout.LayoutParams(-1, -2))

        var isExpanded = false
        accordionHeader.setOnClickListener {
            isExpanded = !isExpanded
            android.transition.TransitionManager.beginDelayedTransition(root)
            accordionBody.visibility = if (isExpanded) View.VISIBLE else View.GONE
            chevron.animate().rotation(if (isExpanded) 180f else 0f).setDuration(220).start()
        }

        root.addView(accordionCard)

        // ═════════════════════════════════════════════════════════════════════
        //  SECTION 4: USER PREFERENCES
        // ═════════════════════════════════════════════════════════════════════
        addSectionHeader("PREFERENCES")

        val userCard = createCard()
        val nameLabel = TextView(this).apply {
            text = "Your Name (Optional)"
            textSize = 13f
            typeface = OutfitFonts.medium(this@MainActivity)
            setTextColor(0xFFCBD5E1.toInt())
        }
        userCard.addView(nameLabel, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })

        val nameInput = EditText(this).apply {
            setText(prefs.name)
            hint = "e.g. Alex"
            setHintTextColor(0xFF334155.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            typeface = OutfitFonts.regular(this@MainActivity)
            textSize = 14.5f
            background = null
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setPadding(0, dp(6), 0, dp(10))
        }
        userCard.addView(nameInput, LinearLayout.LayoutParams(-1, -2))

        val underline = View(this).apply { setBackgroundColor(0xFF1E2436.toInt()) }
        userCard.addView(underline, LinearLayout.LayoutParams(-1, dp(1)))

        nameInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) prefs.name = nameInput.text.toString().trim()
        }

        root.addView(userCard)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(root, ViewGroup.LayoutParams(-1, -2))
        })
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )?.contains(packageName) == true

        if (enabled) {
            (statusDot.background as? GradientDrawable)?.setColor(0xFF22C55E.toInt())
            statusLabel.text = "Service active"
            statusLabel.setTextColor(0xFFFFFFFF.toInt())
            statusSub.text = "The Fleench bubble is active on your screen"
        } else {
            (statusDot.background as? GradientDrawable)?.setColor(0xFF475569.toInt())
            statusLabel.text = "Service inactive"
            statusLabel.setTextColor(0xFF94A3B8.toInt())
            statusSub.text = "Tap below to enable in Accessibility settings"
        }
    }
}
