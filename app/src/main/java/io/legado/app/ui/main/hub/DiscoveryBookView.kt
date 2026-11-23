package io.legado.app.ui.main.hub

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.glide.ImageLoader
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.secondaryTextColor
import io.legado.app.utils.dpToPx

internal class DiscoveryBookView(context: Context) : LinearLayout(context) {
    private val coverFrame = object : FrameLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(
                (MeasureSpec.getSize(widthMeasureSpec) * 1.4f).toInt(), MeasureSpec.EXACTLY,
            ))
        }
    }
    private val fallback = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 17f
        setTextColor(context.primaryTextColor)
        setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
    }
    private val cover = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val name = TextView(context).apply {
        textSize = 14f
        setTextColor(context.primaryTextColor)
        setTypeface(typeface, Typeface.BOLD)
        minLines = 2
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        setPadding(0, 7.dpToPx(), 0, 0)
    }
    private val author = TextView(context).apply {
        textSize = 12f
        setTextColor(context.secondaryTextColor)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    init {
        orientation = VERTICAL
        coverFrame.background = GradientDrawable().apply {
            setColor((context.primaryColor and 0x00ffffff) or 0x18000000)
            cornerRadius = 6.dpToPx().toFloat()
        }
        coverFrame.clipToOutline = true
        coverFrame.addView(fallback, FrameLayout.LayoutParams(-1, -1))
        coverFrame.addView(cover, FrameLayout.LayoutParams(-1, -1))
        addView(coverFrame, LayoutParams(-1, -2))
        addView(name, LayoutParams(-1, -2))
        addView(author, LayoutParams(-1, -2))
        isClickable = true
        isFocusable = true
        val attributes = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        foreground = attributes.getDrawable(0)
        attributes.recycle()
    }

    fun bind(book: SearchBook, rank: Int, onClick: () -> Unit) {
        fallback.text = book.name
        name.text = "$rank. ${book.name}"
        author.text = book.author.ifBlank { "佚名" }
        contentDescription = "第${rank}本，${book.name}，${book.author}"
        ImageLoader.load(context, book.coverUrl).into(cover)
        setOnClickListener { onClick() }
    }

    fun showLoading() {
        fallback.text = "加载中"
        name.text = "正在获取作品"
        author.text = " "
        isClickable = false
        isFocusable = false
        contentDescription = "正在加载作品"
    }
}
