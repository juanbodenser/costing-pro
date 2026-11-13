package com.jboden.costingpro.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.net.Uri
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.ColorInt
import com.jboden.costingpro.R
import com.jboden.costingpro.data.PhotoStore
import com.jboden.costingpro.domain.MarginStatus
import com.jboden.costingpro.ui.common.Formats
import com.jboden.costingpro.ui.common.labelRes
import com.jboden.costingpro.ui.recipes.BreakdownRow
import com.jboden.costingpro.ui.recipes.RecipeSummary
import com.jboden.costingpro.ui.components.wordRes
import java.io.IOException
import java.io.OutputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A one-page A4 PDF of a recipe: photo, result block, breakdown table and allergens.
 * The figures come from [RecipeSummary], so they are the same as on screen, and the colours
 * keep their meaning: cost in ink, price in amber, margin in its state colour next to its word.
 */
class RecipePdf(
    private val context: Context,
    private val photos: PhotoStore,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /** Writes the PDF of [summary] into [target]. @throws IOException if it cannot be written. */
    suspend fun export(summary: RecipeSummary, target: Uri) = withContext(Dispatchers.IO) {
        val output = context.contentResolver.openOutputStream(target, "wt") ?: throw IOException("Cannot open $target")
        output.use { write(summary, it) }
    }

    /** Writes the PDF of [summary] into [output], which is not closed. */
    fun write(summary: RecipeSummary, output: OutputStream) {
        val document = PdfDocument()
        try {
            val page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            val photo = summary.photoPath?.takeIf { photos.hasPhoto(it) }?.let { loadPhoto(it) }
            try {
                Drawer(page.canvas, summary, photo).draw()
            } finally {
                photo?.recycle()
                // Always finished, or close() would hide the error with its own.
                document.finishPage(page)
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    /** The photo, upright and small enough for the page, or null if it cannot be read. */
    private fun loadPhoto(path: String): Bitmap? {
        val file = photos.file(path)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= PHOTO_MAX_PIXELS && bounds.outHeight / (sample * 2) >= PHOTO_MAX_PIXELS / 2) {
            sample *= 2
        }
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val degrees = try {
            when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } catch (e: IOException) {
            0f
        }
        if (degrees == 0f) return bitmap
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees) }, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    /** Draws one page. Coordinates are PDF points (1/72 inch). */
    private inner class Drawer(
        private val canvas: Canvas,
        private val summary: RecipeSummary,
        private val photo: Bitmap?,
    ) {
        private var y = MARGIN.toFloat()
        private val left = MARGIN.toFloat()
        private val right = (PAGE_WIDTH - MARGIN).toFloat()
        private val width = right - left

        /** Formats only when there are arguments, so a plain "%" in a text stays as it is. */
        private fun s(id: Int, vararg args: Any): String =
            if (args.isEmpty()) context.getString(id) else context.getString(id, *args)

        private fun money(value: BigDecimal?) = value?.let { Formats.money(it) } ?: s(R.string.value_missing)

        private fun percent(value: BigDecimal?) = value?.let { Formats.percent(it) } ?: s(R.string.value_missing)

        fun draw() {
            drawHeader()
            val rows = summary.breakdown
            // What the page needs below the photo, to decide whether the photo fits.
            val fixedBelow = RESULT_HEIGHT + GAP + TABLE_HEADER + (rows.size + 1) * ROW_HEIGHT + GAP + ALLERGENS_HEIGHT + FOOTER
            val roomForPhoto = PAGE_HEIGHT - MARGIN - y - fixedBelow - GAP
            if (photo != null && roomForPhoto >= PHOTO_MIN_HEIGHT) {
                drawPhoto(photo, minOf(PHOTO_MAX_HEIGHT, roomForPhoto))
            }
            drawResult()
            val allergensTop = PAGE_HEIGHT - MARGIN - FOOTER - ALLERGENS_HEIGHT
            drawTable(rows, bottom = allergensTop - GAP)
            y = allergensTop
            drawAllergens()
            drawFooter()
        }

        private fun drawHeader() {
            canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 6f, fill(TEAL))
            val title = text(22f, INK, Typeface.create(Typeface.SERIF, Typeface.BOLD))
            y += 22f
            canvas.drawText(ellipsize(summary.name, title, width), left, y, title)
            y += 18f
            val yieldText = s(
                R.string.recipe_yield_summary,
                s(R.string.quantity_with_unit, Formats.number(summary.yieldQuantity), s(summary.yieldUnit.labelRes)),
            )
            canvas.drawText(yieldText, left, y, text(11f, INK_MUTED))
            y += GAP
        }

        private fun drawPhoto(bitmap: Bitmap, height: Float) {
            val box = RectF(left, y, right, y + height)
            // Centre crop: fill the box, cutting what overflows.
            val scale = maxOf(box.width() / bitmap.width, box.height() / bitmap.height)
            val matrix = Matrix().apply {
                postScale(scale, scale)
                postTranslate(box.centerX() - bitmap.width * scale / 2, box.centerY() - bitmap.height * scale / 2)
            }
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(box, CORNER, CORNER, Path.Direction.CW) })
            canvas.drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            canvas.restore()
            y += height + GAP
        }

        /** The highlighted block: margin as the biggest figure, then cost, price, profit. */
        private fun drawResult() {
            val box = RectF(left, y, right, y + RESULT_HEIGHT)
            canvas.drawRoundRect(box, CORNER, CORNER, fill(SURFACE_TINT))
            canvas.drawRoundRect(box, CORNER, CORNER, stroke(OUTLINE))
            val price = summary.price
            val status = summary.status
            val inner = left + PADDING
            val column = (width - 2 * PADDING) / 4

            // Row 1: labels and figures.
            val labelY = y + PADDING + 9f
            val figureY = labelY + 24f
            val label = text(9f, INK_MUTED)
            val figure = text(16f, INK, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
            fun cell(index: Int, labelText: String, value: String, @ColorInt color: Int, size: Float = 16f) {
                val x = inner + column * index
                canvas.drawText(ellipsize(labelText, label, column - 8f), x, labelY, label)
                figure.color = color
                figure.textSize = size
                canvas.drawText(value, x, figureY, figure)
            }
            cell(0, s(R.string.detail_unit_cost), money(summary.cost.unitCost), INK)
            cell(1, s(R.string.detail_net_price), money(price?.netPrice), AMBER)
            cell(2, s(R.string.detail_price_with_vat), money(price?.priceWithVat), AMBER)
            cell(3, s(R.string.result_margin), percent(price?.marginPct), status?.pdfColor() ?: INK, size = 22f)
            if (status != null) {
                val word = text(10f, status.pdfColor(), Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
                val x = inner + column * 3
                canvas.drawCircle(x + 3f, figureY + 12f, 3f, fill(status.pdfColor()))
                canvas.drawText(s(status.wordRes()), x + 10f, figureY + 15.5f, word)
            }

            // Row 2: the rest, smaller, in ink.
            val detailY = y + RESULT_HEIGHT - PADDING - 16f
            val small = text(9.5f, INK)
            val parts = listOfNotNull(
                "${s(R.string.totals_raw_material)} ${money(summary.cost.rawMaterialUnitCost)}",
                "${s(R.string.totals_extras)} ${money(summary.cost.extrasUnitCost)}",
                "${s(R.string.pdf_batch_cost)} ${money(summary.cost.batchCost)}",
            )
            canvas.drawText(ellipsize(parts.joinToString("  ·  "), small, width - 2 * PADDING), inner, detailY, small)
            val priceParts = if (price == null) {
                listOf(s(R.string.pdf_no_price))
            } else {
                listOf(
                    "${s(R.string.result_profit_unit)} ${money(price.profitPerUnit)}",
                    "${s(R.string.result_profit_batch)} ${money(price.profitPerBatch)}",
                    s(R.string.result_food_cost, percent(price.foodCostPct)),
                    "${s(R.string.pricing_markup)} ${percent(price.markupPct)}",
                )
            }
            canvas.drawText(ellipsize(priceParts.joinToString("  ·  "), small, width - 2 * PADDING), inner, detailY + 13f, small)
            y += RESULT_HEIGHT + GAP
        }

        private fun drawTable(rows: List<BreakdownRow>, bottom: Float) {
            val section = text(12f, TEAL, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
            canvas.drawText(s(R.string.breakdown_title), left, y + 12f, section)
            y += 20f

            val shareRight = right
            val costRight = right - 70f
            val quantityRight = right - 160f
            val nameWidth = quantityRight - 70f - left

            val header = text(9f, INK_MUTED, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
            canvas.drawText(s(R.string.pdf_col_item), left, y + 9f, header)
            drawRight(s(R.string.pdf_col_quantity), quantityRight, y + 9f, header)
            drawRight(s(R.string.pdf_col_cost), costRight, y + 9f, header)
            drawRight(s(R.string.pdf_col_share), shareRight, y + 9f, header)
            y += TABLE_HEADER - 4f
            canvas.drawLine(left, y, right, y, stroke(TEAL, 1f))
            y += 4f

            // Keep one row for the total; if the rows do not fit, the last one says how many are left out.
            val capacity = ((bottom - y) / ROW_HEIGHT).toInt() - 1
            val shown = if (rows.size <= capacity) rows else rows.take(maxOf(capacity - 1, 0))
            val name = text(10f, INK)
            val tag = text(7.5f, PLUM, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
            val number = text(10f, INK)
            val muted = text(9.5f, INK_MUTED)
            shown.forEachIndexed { index, row ->
                val baseline = y + ROW_HEIGHT - 6f
                var labelText = row.name ?: s(R.string.totals_extras)
                val tagText = when {
                    row.isSubRecipe -> s(R.string.line_sub_recipe).uppercase(Locale.ROOT)
                    row.isExtra -> s(R.string.breakdown_extra).uppercase(Locale.ROOT)
                    else -> null
                }
                if (tagText != null) {
                    tag.color = if (row.isSubRecipe) PLUM else INK_MUTED
                    val tagWidth = tag.measureText(tagText)
                    labelText = ellipsize(labelText, name, nameWidth - tagWidth - 8f)
                    canvas.drawText(labelText, left, baseline, name)
                    canvas.drawText(tagText, left + name.measureText(labelText) + 6f, baseline - 0.5f, tag)
                } else {
                    canvas.drawText(ellipsize(labelText, name, nameWidth), left, baseline, name)
                }
                if (row.quantity != null && row.unit != null) {
                    val quantity = s(R.string.quantity_with_unit, Formats.number(row.quantity), s(row.unit.labelRes))
                    drawRight(quantity, quantityRight, baseline, muted)
                }
                drawRight(money(row.cost), costRight, baseline, number)
                drawRight(percent(row.sharePct), shareRight, baseline, muted)
                y += ROW_HEIGHT
                if (index < shown.lastIndex) canvas.drawLine(left, y, right, y, stroke(OUTLINE, 0.5f))
            }
            if (shown.size < rows.size) {
                val hidden = rows.size - shown.size
                canvas.drawText(
                    context.resources.getQuantityString(R.plurals.pdf_more_rows, hidden, hidden),
                    left,
                    y + ROW_HEIGHT - 6f,
                    muted,
                )
                y += ROW_HEIGHT
            }
            canvas.drawLine(left, y, right, y, stroke(TEAL, 1f))
            val bold = text(10.5f, INK, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
            canvas.drawText(s(R.string.pdf_batch_cost), left, y + ROW_HEIGHT - 5f, bold)
            drawRight(money(summary.cost.batchCost), costRight, y + ROW_HEIGHT - 5f, bold)
            y += ROW_HEIGHT
        }

        private fun drawAllergens() {
            val section = text(12f, TEAL, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD))
            canvas.drawText(s(R.string.detail_allergens), left, y + 12f, section)
            y += 28f
            val body = text(10.5f, INK)
            val allergens = summary.allergens
            val content = if (allergens.isEmpty()) {
                body.color = INK_MUTED
                s(R.string.detail_no_allergens)
            } else {
                allergens.joinToString("  ·  ") { s(it.labelRes) }
            }
            wrap(content, body, width).take(3).forEach { line ->
                canvas.drawText(line, left, y, body)
                y += 15f
            }
        }

        private fun drawFooter() {
            val baseline = (PAGE_HEIGHT - MARGIN / 2).toFloat()
            val date = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).format(Date(now()))
            canvas.drawText(s(R.string.pdf_footer, date), left, baseline, text(8.5f, INK_MUTED))
        }

        private fun drawRight(value: String, x: Float, baseline: Float, paint: Paint) {
            canvas.drawText(value, x - paint.measureText(value), baseline, paint)
        }
    }

    private fun text(size: Float, @ColorInt color: Int, typeface: Typeface = Typeface.SANS_SERIF) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            this.typeface = typeface
            // Tabular figures, so amounts line up in columns.
            fontFeatureSettings = "tnum"
        }

    private fun fill(@ColorInt color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun stroke(@ColorInt color: Int, strokeWidth: Float = 0.75f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
    }

    private fun ellipsize(value: String, paint: TextPaint, maxWidth: Float): String =
        TextUtils.ellipsize(value, paint, maxWidth, TextUtils.TruncateAt.END).toString()

    /** Splits [value] into lines no wider than [maxWidth], at spaces. */
    private fun wrap(value: String, paint: TextPaint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        var current = ""
        value.split(' ').forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                current = candidate
            } else {
                lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    @ColorInt
    private fun MarginStatus.pdfColor(): Int = when (this) {
        MarginStatus.GOOD -> MARGIN_GOOD
        MarginStatus.FAIR -> MARGIN_FAIR
        MarginStatus.LOW -> MARGIN_LOW
    }

    private companion object {
        // A4 in points.
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN = 40

        const val GAP = 16f
        const val PADDING = 14f
        const val CORNER = 12f
        const val RESULT_HEIGHT = 112f
        const val TABLE_HEADER = 20f
        const val ROW_HEIGHT = 20f
        const val ALLERGENS_HEIGHT = 72f
        const val FOOTER = 12f
        const val PHOTO_MAX_HEIGHT = 220f
        const val PHOTO_MIN_HEIGHT = 110f

        /** Width in pixels the photo is decoded at: enough for print, small enough for memory. */
        const val PHOTO_MAX_PIXELS = 1200

        // The light palette of PLAN.md: a printed page is always light.
        const val TEAL = 0xFF1F5F5B.toInt()
        const val AMBER = 0xFFC98A2B.toInt()
        const val PLUM = 0xFF6B4A6E.toInt()
        const val INK = 0xFF1E2A2E.toInt()
        const val INK_MUTED = 0xFF556266.toInt()
        const val OUTLINE = 0xFFD8D2C7.toInt()
        const val SURFACE_TINT = 0xFFFAF7F2.toInt()
        const val MARGIN_GOOD = 0xFF2E7D5B.toInt()
        const val MARGIN_FAIR = 0xFFB7791F.toInt()
        const val MARGIN_LOW = 0xFFB5483A.toInt()
    }
}
