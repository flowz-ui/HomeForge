package app.subtile.template

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Subtile widget şablonu. Topluluk paylaşımı da bu formatla yapılır: sadece veri, kod yok.
 *
 * Ölçü birimleri:
 * - Konum ve boyut oranları 0..1 arasıdır (widget genişliği/yüksekliğine göre).
 * - size, radius gibi "dp" değerleri, widget'ın standart boyutuna (1 hücre = 70dp - 30dp) göre
 *   tasarlanır; gerçek widget daha büyük/küçükse otomatik ölçeklenir.
 * - Renkler #RRGGBB veya #AARRGGBB (önce saydamlık).
 */
@Serializable
data class WidgetTemplate(
    val schema: Int = 1,
    val id: String = "",
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

    /** angle CSS gibi: 0 = yukarı, 90 = sağa, 180 = aşağı, 135 = sağ alt köşeye. */
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

    /**
     * source: time, date, battery_percent, greeting, static (static ise `text` alanı kullanılır).
     * x = hizaya göre yatay konum, y = yazının dikey ortası.
     * align: left, center, right. tz: örn. "America/New_York" (boşsa cihaz saati).
     * font: light, regular, medium, bold, black, condensed.
     */
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
        val font: String = "regular",
        val align: String = "left",
        val upper: Boolean = false,
        val tz: String? = null,
    ) : Layer

    /** shape: rect veya circle. x,y sol üst köşe; w/h oran. */
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

    /** ref: yerel görsel anahtarı (URL değil). Çizimi sonraki sürümde. */
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

    /** style: ring veya bar. x,y merkez; size halka için çap, çubuk için genişlik (dp). */
    @Serializable
    @SerialName("battery")
    data class Battery(
        val style: String = "ring",
        override val x: Double,
        override val y: Double,
        val size: Int,
        val color: String,
    ) : Layer

    /** x,y merkez; size çap (dp). color = çentikler, handColor = akrep ve yelkovan. */
    @Serializable
    @SerialName("analog_clock")
    data class AnalogClock(
        override val x: Double,
        override val y: Double,
        val size: Int,
        val color: String,
        val handColor: String = "#FFFFFF",
        val tz: String? = null,
    ) : Layer

    /** Ay takvimi. x,y sol üst köşe; w/h oran. size = gün yazısı (dp). accent = bugünün dairesi. */
    @Serializable
    @SerialName("month_grid")
    data class MonthGrid(
        override val x: Double,
        override val y: Double,
        val w: Double,
        val h: Double,
        val size: Int,
        val color: String,
        val accent: String,
    ) : Layer
}

/**
 * area: [x1, y1, x2, y2] oran. action: izin verilen sabit eylem adı.
 * Şimdilik widget'a dokununca sadece ilk eylem çalışır.
 */
@Serializable
data class TapAction(
    val area: List<Double>,
    @SerialName("do") val action: String,
)
