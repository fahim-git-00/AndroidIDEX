package com.aidex.editor

import android.graphics.Color
import android.text.Editable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import java.util.regex.Pattern

enum class Language { KOTLIN, JAVA, XML, PLAIN }

/**
 * Lightweight regex-based syntax highlighter.
 *
 * Highlights are attached as [ForegroundColorSpan]s directly onto the
 * [Editable] backing the editor — no external grammar engine required.
 */
object SyntaxHighlighter {

    private val COLOR_KEYWORD    = Color.parseColor("#81A1C1")
    private val COLOR_STRING     = Color.parseColor("#A3BE8C")
    private val COLOR_COMMENT    = Color.parseColor("#7A8699")
    private val COLOR_NUMBER     = Color.parseColor("#B48EAD")
    private val COLOR_ANNOTATION = Color.parseColor("#D08770")
    private val COLOR_TAG        = Color.parseColor("#88C0D0")
    private val COLOR_ATTR       = Color.parseColor("#8FBCBB")

    private val KOTLIN_KEYWORDS: Set<String> = setOf(
        "package","import","class","interface","object","fun","val","var","if","else",
        "when","for","while","do","return","break","continue","try","catch","finally",
        "throw","is","as","in","out","by","constructor","init","this","super","null",
        "true","false","typealias","where","sealed","data","enum","annotation",
        "companion","internal","private","protected","public","override","open","abstract",
        "final","lateinit","suspend","inline","noinline","crossinline","reified",
        "operator","infix","vararg","const","external","actual","expect","value"
    )

    private val JAVA_KEYWORDS: Set<String> = setOf(
        "abstract","assert","boolean","break","byte","case","catch","char","class","const",
        "continue","default","do","double","else","enum","extends","final","finally","float",
        "for","goto","if","implements","import","instanceof","int","interface","long",
        "native","new","package","private","protected","public","return","short","static",
        "strictfp","super","switch","synchronized","this","throw","throws","transient",
        "try","void","volatile","while","true","false","null","var","record","yield",
        "sealed","permits"
    )

    private val P_LINE_COMMENT   = Pattern.compile("//[^\\n]*")
    private val P_BLOCK_COMMENT  = Pattern.compile("/\\*[\\s\\S]*?\\*/")
    private val P_STRING_DQ      = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"")
    private val P_STRING_SQ      = Pattern.compile("'(?:\\\\.|[^'\\\\])*'")
    private val P_NUMBER         = Pattern.compile("\\b\\d[\\d_]*(?:\\.\\d+)?[fFdDlL]?\\b")
    private val P_IDENT          = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b")
    private val P_ANNOTATION     = Pattern.compile("@[A-Za-z_][A-Za-z0-9_]*")

    private val P_XML_COMMENT    = Pattern.compile("<!--[\\s\\S]*?-->")
    private val P_XML_DECL       = Pattern.compile("<\\?[\\s\\S]*?\\?>")
    private val P_XML_STRING     = Pattern.compile("\"[^\"]*\"")
    private val P_XML_ATTR       = Pattern.compile("[A-Za-z_][A-Za-z0-9_:\\-]*\\s*=")
    private val P_XML_TAG        = Pattern.compile("</?[A-Za-z_][A-Za-z0-9_:\\-]*")

    fun highlight(editable: Editable, language: Language) {
        for (span in editable.getSpans(0, editable.length, ForegroundColorSpan::class.java)) {
            editable.removeSpan(span)
        }
        when (language) {
            Language.KOTLIN -> highlightCode(editable, KOTLIN_KEYWORDS)
            Language.JAVA   -> highlightCode(editable, JAVA_KEYWORDS)
            Language.XML    -> highlightXml(editable)
            Language.PLAIN  -> Unit
        }
    }

    private fun highlightCode(ed: Editable, keywords: Set<String>) {
        val text = ed.toString()
        applySpans(ed, P_NUMBER, text, COLOR_NUMBER)
        applySpans(ed, P_ANNOTATION, text, COLOR_ANNOTATION)
        applySpans(ed, P_STRING_DQ, text, COLOR_STRING)
        applySpans(ed, P_STRING_SQ, text, COLOR_STRING)

        val m = P_IDENT.matcher(text)
        while (m.find()) {
            if (m.group() in keywords) {
                ed.setSpan(
                    ForegroundColorSpan(COLOR_KEYWORD),
                    m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        applySpans(ed, P_LINE_COMMENT, text, COLOR_COMMENT)
        applySpans(ed, P_BLOCK_COMMENT, text, COLOR_COMMENT)
    }

    private fun highlightXml(ed: Editable) {
        val text = ed.toString()
        applySpans(ed, P_XML_STRING, text, COLOR_STRING)
        applySpans(ed, P_XML_ATTR, text, COLOR_ATTR)
        applySpans(ed, P_XML_TAG, text, COLOR_TAG)
        applySpans(ed, P_XML_DECL, text, COLOR_COMMENT)
        applySpans(ed, P_XML_COMMENT, text, COLOR_COMMENT)
    }

    private fun applySpans(ed: Editable, pattern: Pattern, text: String, color: Int) {
        val m = pattern.matcher(text)
        while (m.find()) {
            ed.setSpan(
                ForegroundColorSpan(color),
                m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
}
