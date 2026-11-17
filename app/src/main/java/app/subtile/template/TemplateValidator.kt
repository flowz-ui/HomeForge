package app.subtile.template

import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Şablonları doğrular. Topluluk fazında yüklenen her şablon da buradan geçecek,
 * böylece kötü niyetli veya bozuk içerik daha render edilmeden elenir.
 */
object TemplateValidator {

    const val SUPPORTED_SCHEMA = 1
    const val MAX_LAYERS = 50
    const val MAX_NAME = 40
    const val MAX_TEXT = 100

    private val COLOR = Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$")
    private val LOCAL_REF = Regex("^[a-zA-Z0-9_-]{1,64}$")
    private val ID = Regex("^[a-z0-9_]{1,40}$")

    val TEXT_SOURCES = setOf("time", "date", "battery_percent", "greeting", "static")
    val ACTIONS = setOf("open_clock_app", "open_calendar", "open_battery", "open_subtile")
    private val SHAPES = setOf("rect", "circle")
    private val BATTERY_STYLES = setOf("ring", "bar")
    private val ALIGNS = setOf("left", "center", "right")

    /** Hata listesi döner. Boşsa şablon geçerlidir. */
    fun validate(t: WidgetTemplate): List<String> {
        val errors = mutableListOf<String>()

        if (t.schema != SUPPORTED_SCHEMA) errors += "Desteklenmeyen schema: ${t.schema}"
        if (t.id.isNotEmpty() && !ID.matches(t.id)) errors += "Geçersiz id: ${t.id}"
        if (t.name.isBlank() || t.name.length > MAX_NAME) errors += "Ad 1-$MAX_NAME karakter olmalı"
        if (t.size.w !in 1..4 || t.size.h !in 1..4) errors += "Boyut 1x1 ile 4x4 arasında olmalı"
        if (t.layers.size > MAX_LAYERS) errors += "En fazla $MAX_LAYERS katman olabilir"

        when (val bg = t.background) {
            is Background.Solid -> {
                checkColor(bg.color, "arka plan rengi", errors)
                checkRadius(bg.radius, errors)
            }
            is Background.Gradient -> {
                if (bg.colors.size !in 2..4) errors += "Gradient 2-4 renk içermeli"
                bg.colors.forEach { checkColor(it, "gradient rengi", errors) }
                checkRadius(bg.radius, errors)
            }
        }

        t.layers.forEachIndexed { i, layer ->
            val tag = "katman ${i + 1}"
            checkRatio(layer.x, "$tag x", errors)
            checkRatio(layer.y, "$tag y", errors)
            when (layer) {
                is Layer.Text -> {
                    if (layer.source !in TEXT_SOURCES) errors += "$tag: bilinmeyen kaynak '${layer.source}'"
                    if (layer.source == "static" && layer.text.isNullOrEmpty()) errors += "$tag: static metin boş"
                    if ((layer.text?.length ?: 0) > MAX_TEXT) errors += "$tag: metin çok uzun"
                    if (layer.size !in 6..200) errors += "$tag: yazı boyutu 6-200 olmalı"
                    if (layer.align !in ALIGNS) errors += "$tag: bilinmeyen hizalama '${layer.align}'"
                    checkFormat(layer.format, tag, errors)
                    checkTimeZone(layer.tz, tag, errors)
                    checkColor(layer.color, "$tag rengi", errors)
                }
                is Layer.Shape -> {
                    if (layer.shape !in SHAPES) errors += "$tag: bilinmeyen şekil '${layer.shape}'"
                    checkRatio(layer.w, "$tag w", errors)
                    checkRatio(layer.h, "$tag h", errors)
                    checkColor(layer.color, "$tag rengi", errors)
                    checkRadius(layer.radius, errors)
                }
                is Layer.Image -> {
                    if (!LOCAL_REF.matches(layer.ref)) errors += "$tag: görsel referansı geçersiz (URL olamaz)"
                    checkRatio(layer.w, "$tag w", errors)
                    checkRatio(layer.h, "$tag h", errors)
                    checkRadius(layer.radius, errors)
                }
                is Layer.Battery -> {
                    if (layer.style !in BATTERY_STYLES) errors += "$tag: bilinmeyen pil stili '${layer.style}'"
                    if (layer.size !in 8..300) errors += "$tag: boyut 8-300 olmalı"
                    checkColor(layer.color, "$tag rengi", errors)
                }
                is Layer.AnalogClock -> {
                    if (layer.size !in 20..300) errors += "$tag: boyut 20-300 olmalı"
                    checkTimeZone(layer.tz, tag, errors)
                    checkColor(layer.color, "$tag rengi", errors)
                    checkColor(layer.handColor, "$tag ibre rengi", errors)
                }
                is Layer.MonthGrid -> {
                    checkRatio(layer.w, "$tag w", errors)
                    checkRatio(layer.h, "$tag h", errors)
                    if (layer.size !in 6..40) errors += "$tag: gün yazı boyutu 6-40 olmalı"
                    checkColor(layer.color, "$tag rengi", errors)
                    checkColor(layer.accent, "$tag vurgu rengi", errors)
                }
            }
        }

        t.actions.forEachIndexed { i, a ->
            val tag = "eylem ${i + 1}"
            if (a.action !in ACTIONS) errors += "$tag: izin verilmeyen eylem '${a.action}'"
            val ok = a.area.size == 4 &&
                a.area.all { it in 0.0..1.0 } &&
                a.area[2] > a.area[0] &&
                a.area[3] > a.area[1]
            if (!ok) errors += "$tag: alan [x1,y1,x2,y2] 0-1 arası ve x2>x1, y2>y1 olmalı"
        }

        return errors
    }

    private fun checkColor(value: String, label: String, errors: MutableList<String>) {
        if (!COLOR.matches(value)) errors += "Geçersiz renk ($label): $value"
    }

    private fun checkRatio(value: Double, label: String, errors: MutableList<String>) {
        if (value.isNaN() || value < 0.0 || value > 1.0) errors += "$label 0-1 arasında olmalı"
    }

    private fun checkRadius(value: Int, errors: MutableList<String>) {
        if (value !in 0..200) errors += "Köşe yarıçapı 0-200 olmalı"
    }

    private fun checkFormat(format: String?, tag: String, errors: MutableList<String>) {
        if (format == null) return
        if (format.length > 30) {
            errors += "$tag: tarih biçimi çok uzun"
            return
        }
        try {
            DateTimeFormatter.ofPattern(format)
        } catch (e: IllegalArgumentException) {
            errors += "$tag: geçersiz tarih biçimi '$format'"
        }
    }

    private fun checkTimeZone(tz: String?, tag: String, errors: MutableList<String>) {
        if (tz == null) return
        try {
            ZoneId.of(tz)
        } catch (e: Exception) {
            errors += "$tag: geçersiz saat dilimi '$tz'"
        }
    }
}
