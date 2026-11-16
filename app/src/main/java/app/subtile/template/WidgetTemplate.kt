package app.subtile.template

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Subtile widget şablonu. Topluluk paylaşımı da bu formatla yapılır: sadece veri, kod yok.
 * Tüm konum ve boyut oranları 0..1 arasıdır (widget genişliği/yüksekliğine göre).
 */
@Serializable
data class WidgetTemplate(
    val schema: Int = 1,
    val name: String,
    val size: GridSize,
    val background: Background,
    val layers: List<Layer> = emptyList(),
    val actions: List<TapAction> = emptyList(),
)

@Serializable
data class GridSize(val w: Int, val h: Int)

@Serializable
sealed interface Background {
    @Serializable
    @SerialName("solid")
    data class Solid(val color: String, val radius: Int = 0) : Background

    @Serializable
    @SerialName("gradient")
    data class Gradient(
        val colors: List<String>,
        val angle: Int = 0,
        val radius: Int = 0,
    ) : Background
}

@Serializable
sealed interface Layer {
    val x: Double
    val y: Double

    /** source: time, date, battery_percent, next_event, static (static ise `text` alanı kullanılır). */
    @Serializable
    @SerialName("text")
    data class Text(
        val source: String,
        val format: String? = null,
        val text: String? = null,
        override val x: Double,
        override val y: Double,
        val size: Int,
        val color: String,
        val font: String = "default",
    ) : Layer

    /** shape: rect veya circle. w/h oran. */
    @Serializable
    @SerialName("shape")
    data class Shape(
        val shape: String = "rect",
        override val x: Double,
        override val y: Double,
        val w: Double,
        val h: Double,
        val color: String,
        val radius: Int = 0,
    ) : Layer

    /** ref: yerel görsel anahtarı (URL değil). */
    @Serializable
    @SerialName("image")
    data class Image(
        val ref: String,
        override val x: Double,
        override val y: Double,
        val w: Double,
        val h: Double,
        val radius: Int = 0,
    ) : Layer

    /** style: ring veya bar. */
    @Serializable
    @SerialName("battery")
    data class Battery(
        val style: String = "ring",
        override val x: Double,
        override val y: Double,
        val size: Int,
        val color: String,
    ) : Layer

    @Serializable
    @SerialName("analog_clock")
    data class AnalogClock(
        override val x: Double,
        override val y: Double,
        val size: Int,
        val color: String,
        val handColor: String = "#FFFFFF",
    ) : Layer
}

/** area: [x1, y1, x2, y2] oran. action: izin verilen sabit eylem adı. */
@Serializable
data class TapAction(
    val area: List<Double>,
    @SerialName("do") val action: String,
)
