package com.example.wordexplainer

import android.content.Context
import android.graphics.Typeface
import java.util.concurrent.ConcurrentHashMap

object OutfitFonts {
    enum class Weight(val assetPath: String) {
        REGULAR("fonts/Outfit-Regular.ttf"),
        MEDIUM("fonts/Outfit-Medium.ttf"),
        SEMIBOLD("fonts/Outfit-SemiBold.ttf"),
        BOLD("fonts/Outfit-Bold.ttf")
    }

    private val cache = ConcurrentHashMap<Weight, Typeface>()

    fun get(context: Context, weight: Weight = Weight.REGULAR): Typeface {
        return cache.getOrPut(weight) {
            Typeface.createFromAsset(context.applicationContext.assets, weight.assetPath)
        }
    }

    fun regular(context: Context) = get(context, Weight.REGULAR)
    fun medium(context: Context) = get(context, Weight.MEDIUM)
    fun semibold(context: Context) = get(context, Weight.SEMIBOLD)
    fun bold(context: Context) = get(context, Weight.BOLD)
}
