package com.example.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.ui.components.UserGuideData
import com.example.ui.i18n.AppLanguage
import java.io.ByteArrayOutputStream

/**
 * Generates a clean multi-page A4 PDF User Guide in Bangla or English using
 * Android's native StaticLayout (ensuring full Unicode Bengali script shaping).
 */
object UserGuidePdfBuilder {

    fun buildUserGuidePdfBytes(
        language: AppLanguage = AppLanguage.BN,
        schoolName: String? = null
    ): ByteArray {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f
        val contentWidth = pageWidth - margin * 2f
        val bottomLimit = pageHeight - 48f

        val sections = UserGuideData.sectionsFor(language)
        val isBn = language == AppLanguage.BN

        val bannerBg = Paint().apply {
            color = Color.rgb(0, 106, 78)
            style = Paint.Style.FILL
        }
        val cardBg = Paint().apply {
            color = Color.rgb(246, 250, 248)
            style = Paint.Style.FILL
        }
        val tipBg = Paint().apply {
            color = Color.rgb(228, 244, 236)
            style = Paint.Style.FILL
        }
        val badgeBg = Paint().apply {
            color = Color.rgb(0, 106, 78)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(195, 212, 204)
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val bannerTitlePaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bannerSubPaint = TextPaint().apply {
            color = Color.rgb(220, 245, 234)
            textSize = 10.5f
            isAntiAlias = true
        }
        val badgeTextPaint = TextPaint().apply {
            color = Color.WHITE
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val sectionTitlePaint = TextPaint().apply {
            color = Color.rgb(12, 65, 48)
            textSize = 12.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val summaryPaint = TextPaint().apply {
            color = Color.rgb(0, 106, 78)
            textSize = 9.8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = TextPaint().apply {
            color = Color.rgb(35, 40, 38)
            textSize = 9.6f
            isAntiAlias = true
        }
        val tipTextPaint = TextPaint().apply {
            color = Color.rgb(18, 72, 54)
            textSize = 9.2f
            isAntiAlias = true
        }
        val footerPaint = TextPaint().apply {
            color = Color.rgb(110, 120, 115)
            textSize = 8.5f
            isAntiAlias = true
        }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        fun drawFooter(c: Canvas, pNum: Int) {
            val leftText = if (isBn) "আমার বিদ্যালয় (My School) — ব্যবহার নির্দেশিকা" else "My School — Official User Guide"
            val rightText = if (isBn) "পৃষ্ঠা $pNum" else "Page $pNum"
            drawLayoutText(c, leftText, margin, pageHeight - 28f, contentWidth * 0.7f, footerPaint, Layout.Alignment.ALIGN_NORMAL)
            drawLayoutText(c, rightText, margin + contentWidth * 0.7f, pageHeight - 28f, contentWidth * 0.3f, footerPaint, Layout.Alignment.ALIGN_OPPOSITE)
        }

        fun startNextPage(): Float {
            drawFooter(canvas, pageNum)
            doc.finishPage(page)
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            return 34f
        }

        // Cover Header Banner on Page 1
        var y = 30f
        val bannerRect = RectF(margin, y, pageWidth - margin, y + 76f)
        canvas.drawRoundRect(bannerRect, 12f, 12f, bannerBg)

        val mainHeading = if (isBn) "আমার বিদ্যালয় — সম্পূর্ণ ব্যবহার নির্দেশিকা" else "My School — Complete User Guide"
        val cleanSchool = schoolName?.trim().orEmpty()
        val subHeading = if (isBn) {
            val prefix = if (cleanSchool.isNotBlank()) "$cleanSchool  •  " else ""
            "${prefix}বাংলা ও ইংরেজি মাধ্যম  •  রিয়েল-টাইম ক্লাউড সিঙ্ক"
        } else {
            val prefix = if (cleanSchool.isNotBlank()) "$cleanSchool  •  " else ""
            "${prefix}Bangla & English  •  Real-Time Cloud Sync"
        }

        drawLayoutText(canvas, mainHeading, margin + 16f, y + 14f, contentWidth - 32f, bannerTitlePaint, Layout.Alignment.ALIGN_NORMAL)
        drawLayoutText(canvas, subHeading, margin + 16f, y + 44f, contentWidth - 32f, bannerSubPaint, Layout.Alignment.ALIGN_NORMAL)
        y += 88f

        // Quick Highlights Box on Page 1
        val highlightsText = if (isBn) {
            "সংক্ষিপ্ত নিয়মাবলী: শিক্ষার্থীর বাংলা নাম বাধ্যতামূলক (ইংরেজি ঐচ্ছিক) • ১০০ নম্বরে ৩৩ পাস, ৫০ নম্বরে ১৭ পাস • মেধা স্থান শুধুমাত্র বার্ষিক পরীক্ষায় • ফলাফল সরাসরি WhatsApp / PDF / PNG-তে পাঠানো যায়।"
        } else {
            "Quick Rules: Bangla student name mandatory (English optional) • 100 marks → 33 pass, 50 marks → 17 pass • Merit rank on Final Exam only • Share results via WhatsApp, PDF & PNG."
        }
        val hiLayout = buildLayout(highlightsText, contentWidth - 24f, tipTextPaint)
        val hiBoxH = hiLayout.height + 16f
        val hiRect = RectF(margin, y, pageWidth - margin, y + hiBoxH)
        canvas.drawRoundRect(hiRect, 8f, 8f, tipBg)
        canvas.drawRoundRect(hiRect, 8f, 8f, borderPaint)
        canvas.save()
        canvas.translate(margin + 12f, y + 8f)
        hiLayout.draw(canvas)
        canvas.restore()
        y += hiBoxH + 14f

        // Render Each Guide Section Card
        sections.forEach { sec ->
            val innerTextW = contentWidth - 28f
            val titleLayout = buildLayout(sec.title, innerTextW - 32f, sectionTitlePaint)
            val summaryLayout = buildLayout(sec.summary, innerTextW, summaryPaint)
            val stepLayouts = sec.steps.map { step ->
                buildLayout("• $step", innerTextW, bodyPaint)
            }
            val tipLayout = sec.tip?.takeIf { it.isNotBlank() }?.let { t ->
                val prefix = if (isBn) "টিপস: " else "Tip: "
                buildLayout("$prefix$t", innerTextW - 16f, tipTextPaint)
            }

            val titleRowH = maxOf(titleLayout.height.toFloat(), 22f)
            val totalCardH = 14f + titleRowH + 6f + summaryLayout.height + 8f +
                stepLayouts.sumOf { it.height + 5 } +
                (if (tipLayout != null) tipLayout.height + 20f else 0f) + 10f

            if (y + totalCardH > bottomLimit) {
                y = startNextPage()
            }

            val cardRect = RectF(margin, y, pageWidth - margin, y + totalCardH)
            canvas.drawRoundRect(cardRect, 10f, 10f, cardBg)
            canvas.drawRoundRect(cardRect, 10f, 10f, borderPaint)

            var cy = y + 12f
            // Number badge
            val badgeCx = margin + 24f
            val badgeCy = cy + 10f
            canvas.drawCircle(badgeCx, badgeCy, 11f, badgeBg)
            drawLayoutText(canvas, sec.number, badgeCx - 10f, cy + 3f, 20f, badgeTextPaint, Layout.Alignment.ALIGN_CENTER)

            // Title
            canvas.save()
            canvas.translate(margin + 42f, cy)
            titleLayout.draw(canvas)
            canvas.restore()
            cy += titleRowH + 6f

            // Summary
            canvas.save()
            canvas.translate(margin + 14f, cy)
            summaryLayout.draw(canvas)
            canvas.restore()
            cy += summaryLayout.height + 8f

            // Bullet steps
            stepLayouts.forEach { sl ->
                canvas.save()
                canvas.translate(margin + 14f, cy)
                sl.draw(canvas)
                canvas.restore()
                cy += sl.height + 5f
            }

            // Optional tip box
            if (tipLayout != null) {
                cy += 4f
                val tipBoxH = tipLayout.height + 12f
                val tRect = RectF(margin + 14f, cy, pageWidth - margin - 14f, cy + tipBoxH)
                canvas.drawRoundRect(tRect, 6f, 6f, tipBg)
                canvas.save()
                canvas.translate(margin + 22f, cy + 6f)
                tipLayout.draw(canvas)
                canvas.restore()
            }

            y += totalCardH + 12f
        }

        drawFooter(canvas, pageNum)
        doc.finishPage(page)

        return ByteArrayOutputStream().use { out ->
            doc.writeTo(out)
            doc.close()
            out.toByteArray()
        }
    }

    private fun buildLayout(
        text: String,
        width: Float,
        paint: TextPaint,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL
    ): StaticLayout {
        val safeW = width.toInt().coerceAtLeast(24)
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, safeW)
            .setAlignment(alignment)
            .setLineSpacing(2f, 1.08f)
            .setIncludePad(false)
            .build()
    }

    private fun drawLayoutText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        width: Float,
        paint: TextPaint,
        alignment: Layout.Alignment
    ) {
        val layout = buildLayout(text, width, paint, alignment)
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }
}
