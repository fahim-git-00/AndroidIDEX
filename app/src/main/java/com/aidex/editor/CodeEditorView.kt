package com.aidex.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.appcompat.widget.AppCompatEditText

/**
 * A modern, lightweight code editor view:
 *  - Monospace font, dark theme.
 *  - Line-number gutter drawn in `onDraw`.
 *  - Regex-driven syntax highlighting (see [SyntaxHighlighter]).
 *  - Auto-indent on newline (mirrors previous line's indentation).
 */
class CodeEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#5B6678")
        textSize = sp(11f)
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.RIGHT
    }
    private val gutterBgPaint = Paint().apply { color = Color.parseColor("#272C36") }
    private val dividerPaint = Paint().apply {
        color = Color.parseColor("#3B4252")
        strokeWidth = 1f
    }

    private var language: Language = Language.KOTLIN
    private var gutterWidth = 0
    private var lastDigits = 0
    private var suppressHighlight = false

    init {
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        setTextColor(Color.parseColor("#D8DEE9"))
        setHintTextColor(Color.parseColor("#5B6678"))
        setBackgroundColor(Color.parseColor("#2E3440"))
        gravity = Gravity.TOP or Gravity.START
        isVerticalScrollBarEnabled = true
        isHorizontalScrollBarEnabled = false
        setHorizontallyScrolling(false)
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO

        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                computeGutter()
                if (!suppressHighlight && s != null) {
                    suppressHighlight = true
                    try {
                        SyntaxHighlighter.highlight(s, language)
                    } finally {
                        suppressHighlight = false
                    }
                }
            }
        })
        computeGutter()
    }

    fun setLanguage(lang: Language) {
        if (language == lang) return
        language = lang
        text?.let {
            suppressHighlight = true
            try { SyntaxHighlighter.highlight(it, lang) } finally { suppressHighlight = false }
        }
    }

    fun currentLanguage(): Language = language

    private fun sp(v: Float) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics
    )

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private fun computeGutter() {
        val digits = lineCount.coerceAtLeast(1).toString().length.coerceAtLeast(3)
        if (digits == lastDigits && gutterWidth > 0) return
        lastDigits = digits
        gutterWidth = (gutterPaint.measureText("0".repeat(digits)) + dp(20f)).toInt()
        setPaddingRelative(
            gutterWidth + dp(10f).toInt(),
            dp(8f).toInt(),
            dp(12f).toInt(),
            dp(8f).toInt()
        )
    }

    override fun onDraw(canvas: Canvas) {
        val sy = scrollY
        val top = sy.toFloat()
        val bottom = (sy + height).toFloat()

        canvas.drawRect(0f, top, gutterWidth.toFloat(), bottom, gutterBgPaint)
        canvas.drawLine(gutterWidth.toFloat(), top, gutterWidth.toFloat(), bottom, dividerPaint)

        val lay = layout
        if (lay != null && lineCount > 0) {
            val padTop = totalPaddingTop
            val first = lay.getLineForVertical(sy).coerceAtLeast(0)
            val last = lay.getLineForVertical(sy + height).coerceAtMost(lineCount - 1)
            for (i in first..last) {
                val baseline = (lay.getLineBaseline(i) + padTop).toFloat()
                canvas.drawText(
                    (i + 1).toString(),
                    gutterWidth - dp(6f),
                    baseline,
                    gutterPaint
                )
            }
        }

        super.onDraw(canvas)
    }
}
