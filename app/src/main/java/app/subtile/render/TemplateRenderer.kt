package app.subtile.render

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.BatteryManager
import app.subtile.template.Background
import app.subtile.template.Layer
import app.subtile.template.TemplateParser
import app.subtile.template.WidgetTemplate
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Çizim sırasında kullanılan canlı veriler. */
data class RenderData(val now: ZonedDateTime, val batteryPercent: Int)

/** Pil yüzdesini okur. */
object BatteryReader {
    fun percent(context: Context): Int {
        val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level < 0 || scale <= 0) 0 else level * 100 / scale
    }
}

/** assets/templates/collection.json içindeki resmi widget koleksiyonu. */
object TemplateCollection {
    @Volatile
    private var cache: List<WidgetTemplate>? = null

    fun load(context: Context): List<WidgetTemplate> = loadWithErrors(context).templates

    fun loadWithErrors(context: Context): app.subtile.template.ListResult {
        val raw = context.applicationContext.assets
            .open("templates/collection.json").bufferedReader().use { it.readText() }
        val result = TemplateParser.parseList(raw)
        cache = result.templates
        return result
    }

    fun find(context: Context, id: String): WidgetTemplate? =
        try {
            (cache ?: load(context)).firstOrNull { it.id == id }
        } catch (e: Exception) {
            null
        }
}

/**
 * Şablonu Canvas ile bitmap'e çizer. Widget'ta gösterilen görsel de uygulamadaki önizleme de bu çizimdir.
 */
object TemplateRenderer {

    /** Android'in önerdiği widget ölçüsü: n hücre = 70n - 30 dp. */
    fun nominalDp(cells: Int): Float = 70f * cells - 30f

    fun render(
        t: WidgetTemplate,
        widthDp: Float,
        heightDp: Float,
        density: Float,
        data: RenderData,
    ): Bitmap {
        val w = max(1, (widthDp * density).roundToInt())
        val h = max(1, (heightDp * density).roundToInt())
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Şablon standart boyuta göre tasarlandı; gerçek widget farklıysa dp değerleri ölçeklenir.
        val scale = min(widthDp / nominalDp(t.size.w), heightDp / nominalDp(t.size.h)).coerceIn(0.5f, 3f)
        val u = density * scale

        drawBackground(canvas, t.background, w.toFloat(), h.toFloat(), u)
        t.layers.forEach { layer ->
            try {
                drawLayer(canvas, layer, w.toFloat(), h.toFloat(), u, data)
            } catch (e: Exception) {
                // Tek bir katmandaki hata widget'ın tamamını bozmasın.
            }
        }
        return bitmap
    }

    // ---------- arka plan ----------

    private fun drawBackground(c: Canvas, bg: Background, w: Float, h: Float, u: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val radius = when (bg) {
            is Background.Solid -> bg.radius * u
            is Background.Gradient -> bg.radius * u
        }
        when (bg) {
            is Background.Solid -> {
                paint.color = parseColor(bg.color)
            }
            is Background.Gradient -> {
                val colors = bg.colors.map { parseColor(it) }.toIntArray()
                val a = Math.toRadians(bg.angle.toDouble())
                val dx = sin(a).toFloat()
                val dy = (-cos(a)).toFloat()
                val half = (abs(w * dx) + abs(h * dy)) / 2f
                val cx = w / 2f
                val cy = h / 2f
                paint.shader = LinearGradient(
                    cx - dx * half, cy - dy * half,
                    cx + dx * half, cy + dy * half,
                    colors, null, Shader.TileMode.CLAMP,
                )
            }
        }
        c.drawRoundRect(RectF(0f, 0f, w, h), radius, radius, paint)
    }

    // ---------- katmanlar ----------

    private fun drawLayer(c: Canvas, layer: Layer, w: Float, h: Float, u: Float, data: RenderData) {
        when (layer) {
            is Layer.Text -> drawText(c, layer, w, h, u, data)
            is Layer.Shape -> drawShape(c, layer, w, h, u)
            is Layer.Image -> Unit // görsel katmanı sonraki sürümde
            is Layer.Battery -> drawBattery(c, layer, w, h, u, data)
            is Layer.AnalogClock -> drawAnalogClock(c, layer, w, h, u, data)
            is Layer.MonthGrid -> drawMonthGrid(c, layer, w, h, u, data)
        }
    }

    private fun drawText(c: Canvas, l: Layer.Text, w: Float, h: Float, u: Float, data: RenderData) {
        val locale = Locale.getDefault()
        val raw = when (l.source) {
            "time" -> format(zoned(data, l.tz), l.format ?: "HH:mm", locale)
            "date" -> format(zoned(data, l.tz), l.format ?: "d MMMM", locale)
            "battery_percent" -> "${data.batteryPercent}%"
            "greeting" -> greeting(zoned(data, l.tz).hour, locale)
            "static" -> l.text ?: ""
            else -> return
        }
        val value = if (l.upper) raw.uppercase(locale) else raw

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = parseColor(l.color)
            typeface = fontFor(l.font)
            textSize = l.size * u
            textAlign = when (l.align) {
                "center" -> Paint.Align.CENTER
                "right" -> Paint.Align.RIGHT
                else -> Paint.Align.LEFT
            }
        }
        val px = (l.x * w).toFloat()
        val py = (l.y * h).toFloat()

        // Yazı sığmıyorsa en fazla yarısına kadar küçült.
        val available = when (l.align) {
            "center" -> min(px, w - px) * 2f
            "right" -> px
            else -> w - px
        }
        val textWidth = paint.measureText(value)
        if (available > 0f && textWidth > available) {
            paint.textSize = paint.textSize * max(0.5f, available / textWidth)
        }
        drawTextMiddle(c, value, px, py, paint)
    }

    private fun drawShape(c: Canvas, l: Layer.Shape, w: Float, h: Float, u: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = parseColor(l.color) }
        val left = (l.x * w).toFloat()
        val top = (l.y * h).toFloat()
        val rect = RectF(left, top, left + (l.w * w).toFloat(), top + (l.h * h).toFloat())
        if (l.shape == "circle") {
            c.drawOval(rect, paint)
        } else {
            val r = l.radius * u
            c.drawRoundRect(rect, r, r, paint)
        }
    }

    private fun drawBattery(c: Canvas, l: Layer.Battery, w: Float, h: Float, u: Float, data: RenderData) {
        val cx = (l.x * w).toFloat()
        val cy = (l.y * h).toFloat()
        val pct = data.batteryPercent.coerceIn(0, 100)
        val main = parseColor(l.color)
        val track = withAlpha(main, 0x40)

        if (l.style == "bar") {
            val bw = l.size * u
            val bh = max(6f * u, bw * 0.1f)
            val rect = RectF(cx - bw / 2f, cy - bh / 2f, cx + bw / 2f, cy + bh / 2f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = track }
            c.drawRoundRect(rect, bh / 2f, bh / 2f, paint)
            if (pct > 0) {
                paint.color = main
                val fillRight = rect.left + max(bh, bw * pct / 100f)
                c.drawRoundRect(RectF(rect.left, rect.top, fillRight, rect.bottom), bh / 2f, bh / 2f, paint)
            }
        } else {
            val d = l.size * u
            val stroke = d * 0.14f
            val rect = RectF(
                cx - d / 2f + stroke / 2f, cy - d / 2f + stroke / 2f,
                cx + d / 2f - stroke / 2f, cy + d / 2f - stroke / 2f,
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
                color = track
            }
            c.drawArc(rect, 0f, 360f, false, paint)
            if (pct > 0) {
                paint.color = main
                c.drawArc(rect, -90f, 360f * pct / 100f, false, paint)
            }
        }
    }

    private fun drawAnalogClock(c: Canvas, l: Layer.AnalogClock, w: Float, h: Float, u: Float, data: RenderData) {
        val cx = (l.x * w).toFloat()
        val cy = (l.y * h).toFloat()
        val r = l.size * u / 2f
        val t = zoned(data, l.tz)

        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = parseColor(l.color)
        }
        for (i in 0 until 12) {
            val major = i % 3 == 0
            val len = r * (if (major) 0.13f else 0.07f)
            tick.strokeWidth = r * (if (major) 0.05f else 0.03f)
            val a = Math.toRadians(i * 30.0)
            val s = sin(a).toFloat()
            val co = cos(a).toFloat()
            c.drawLine(cx + s * (r - len), cy - co * (r - len), cx + s * r, cy - co * r, tick)
        }

        val minutes = t.minute + t.second / 60f
        val hours = (t.hour % 12) + minutes / 60f
        val hand = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = parseColor(l.handColor)
        }
        fun drawHand(angleDeg: Float, length: Float, width: Float) {
            val a = Math.toRadians(angleDeg.toDouble())
            hand.strokeWidth = width
            c.drawLine(cx, cy, cx + sin(a).toFloat() * length, cy - cos(a).toFloat() * length, hand)
        }
        drawHand(hours * 30f, r * 0.5f, r * 0.075f)
        drawHand(minutes * 6f, r * 0.76f, r * 0.05f)
        hand.style = Paint.Style.FILL
        c.drawCircle(cx, cy, r * 0.065f, hand)
    }

    private fun drawMonthGrid(c: Canvas, l: Layer.MonthGrid, w: Float, h: Float, u: Float, data: RenderData) {
        val locale = Locale.getDefault()
        val left = (l.x * w).toFloat()
        val top = (l.y * h).toFloat()
        val cellW = (l.w * w).toFloat() / 7f
        val cellH = (l.h * h).toFloat() / 7f // 1 başlık satırı + en fazla 6 hafta
        val today = data.now.toLocalDate()
        val ym = YearMonth.from(today)
        val startOffset = ym.atDay(1).dayOfWeek.value - 1 // hafta Pazartesi başlar

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = l.size * u
            typeface = fontFor("medium")
        }
        val text = parseColor(l.color)

        // Gün başlıkları
        paint.color = withAlpha(text, 0x99)
        for (i in 0 until 7) {
            val label = DayOfWeek.of(i + 1).getDisplayName(TextStyle.NARROW, locale)
            drawTextMiddle(c, label, left + i * cellW + cellW / 2f, top + cellH / 2f, paint)
        }

        // Günler
        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = parseColor(l.accent) }
        for (day in 1..ym.lengthOfMonth()) {
            val index = startOffset + day - 1
            val col = index % 7
            val row = index / 7 + 1
            val cx = left + col * cellW + cellW / 2f
            val cy = top + row * cellH + cellH / 2f
            if (day == today.dayOfMonth) {
                c.drawCircle(cx, cy, min(cellW, cellH) * 0.46f, accent)
                paint.color = Color.WHITE
            } else {
                paint.color = text
            }
            drawTextMiddle(c, day.toString(), cx, cy, paint)
        }
    }

    // ---------- yardımcılar ----------

    private fun drawTextMiddle(c: Canvas, text: String, x: Float, middleY: Float, paint: Paint) {
        val fm = paint.fontMetrics
        c.drawText(text, x, middleY - (fm.ascent + fm.descent) / 2f, paint)
    }

    private fun zoned(data: RenderData, tz: String?): ZonedDateTime {
        if (tz.isNullOrEmpty()) return data.now
        return try {
            data.now.withZoneSameInstant(ZoneId.of(tz))
        } catch (e: Exception) {
            data.now
        }
    }

    private fun format(t: ZonedDateTime, pattern: String, locale: Locale): String =
        try {
            t.format(DateTimeFormatter.ofPattern(pattern, locale))
        } catch (e: Exception) {
            "?"
        }

    private fun greeting(hour: Int, locale: Locale): String {
        val tr = locale.language == "tr"
        return when (hour) {
            in 5..11 -> if (tr) "Günaydın" else "Good morning"
            in 12..17 -> if (tr) "İyi günler" else "Good afternoon"
            in 18..21 -> if (tr) "İyi akşamlar" else "Good evening"
            else -> if (tr) "İyi geceler" else "Good night"
        }
    }

    private fun fontFor(font: String): Typeface = when (font) {
        "light" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
        "medium" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
        "bold" -> Typeface.create("sans-serif", Typeface.BOLD)
        "black" -> Typeface.create("sans-serif-black", Typeface.NORMAL)
        "condensed" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        else -> Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private fun parseColor(value: String): Int =
        try {
            Color.parseColor(value)
        } catch (e: IllegalArgumentException) {
            Color.MAGENTA
        }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)
}
