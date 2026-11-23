package io.legado.app.ui.main.hub

import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.cardview.widget.CardView
import android.content.res.ColorStateList
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.utils.dpToPx

internal fun LinearLayout.heading(text: String, size: Float = 22f) {
    addView(TextView(context).apply {
        this.text = text
        textSize = size
        setTextColor(context.primaryTextColor)
        setTypeface(typeface, Typeface.BOLD)
        setPadding(0, 16.dpToPx(), 0, 12.dpToPx())
    })
}

internal fun LinearLayout.note(text: String): TextView = TextView(context).also {
    it.text = text
    it.textSize = 15f
    it.setTextColor(context.secondaryTextColor)
    it.setLineSpacing(4.dpToPx().toFloat(), 1f)
    it.setPadding(0, 0, 0, 12.dpToPx())
    addView(it)
}

internal fun LinearLayout.action(label: String, action: () -> Unit): AppCompatButton =
    AppCompatButton(context).also {
        it.text = label
        it.textSize = 16f
        it.isAllCaps = false
        it.minHeight = 52.dpToPx()
        it.backgroundTintList = ColorStateList.valueOf(context.primaryColor)
        it.setTextColor(context.getPrimaryTextColor(ColorUtils.isColorLight(context.primaryColor)))
        it.setOnClickListener { action() }
        addView(it, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 8.dpToPx() })
    }

internal fun LinearLayout.card(title: String, detail: String, action: (() -> Unit)? = null) {
    val card = CardView(context).apply {
        radius = 16.dpToPx().toFloat()
        cardElevation = 1.dpToPx().toFloat()
        setCardBackgroundColor(context.backgroundColor)
    }
    val body = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(16.dpToPx(), 0, 16.dpToPx(), 8.dpToPx())
        heading(title, 18f)
        note(detail)
    }
    card.addView(body)
    if (action != null) {
        card.isClickable = true
        card.isFocusable = true
        card.setOnClickListener { action() }
    }
    addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = 12.dpToPx() })
}
