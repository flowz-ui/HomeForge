package app.subtile.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.RemoteViews
import app.subtile.MainActivity
import app.subtile.R
import app.subtile.render.BatteryReader
import app.subtile.render.RenderData
import app.subtile.render.TemplateCollection
import app.subtile.render.TemplateRenderer
import app.subtile.template.WidgetTemplate
import java.time.ZonedDateTime

/** Her resmi widget bu sınıfın küçük bir alt sınıfıdır; hangi şablonu çizeceğini id belirler. */
abstract class TemplateWidgetProvider(private val templateId: String) : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetUpdater.update(context, manager, it, templateId) }
        TickReceiver.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        WidgetUpdater.update(context, manager, appWidgetId, templateId)
    }

    override fun onEnabled(context: Context) {
        TickReceiver.schedule(context)
    }
}

class IosClockDark : TemplateWidgetProvider("ios_clock_dark")
class IosClockLight : TemplateWidgetProvider("ios_clock_light")
class IosClockWide : TemplateWidgetProvider("ios_clock_wide")
class IosAnalogDark : TemplateWidgetProvider("ios_analog_dark")
class IosAnalogLight : TemplateWidgetProvider("ios_analog_light")
class IosDateLight : TemplateWidgetProvider("ios_date_light")
class IosDateDark : TemplateWidgetProvider("ios_date_dark")
class IosMonth : TemplateWidgetProvider("ios_month")
class IosBatteryRing : TemplateWidgetProvider("ios_battery_ring")
class IosBatteryBar : TemplateWidgetProvider("ios_battery_bar")
class IosBatteryClock : TemplateWidgetProvider("ios_battery_clock")
class IosGreeting : TemplateWidgetProvider("ios_greeting")
class IosWorldClock : TemplateWidgetProvider("ios_world_clock")
class IosGradientClock : TemplateWidgetProvider("ios_gradient_clock")

/** Dakikalık güncelleme için tüm sağlayıcıların listesi. */
object WidgetRegistry {
    val providers: List<Pair<Class<out TemplateWidgetProvider>, String>> = listOf(
        IosClockDark::class.java to "ios_clock_dark",
        IosClockLight::class.java to "ios_clock_light",
        IosClockWide::class.java to "ios_clock_wide",
        IosAnalogDark::class.java to "ios_analog_dark",
        IosAnalogLight::class.java to "ios_analog_light",
        IosDateLight::class.java to "ios_date_light",
        IosDateDark::class.java to "ios_date_dark",
        IosMonth::class.java to "ios_month",
        IosBatteryRing::class.java to "ios_battery_ring",
        IosBatteryBar::class.java to "ios_battery_bar",
        IosBatteryClock::class.java to "ios_battery_clock",
        IosGreeting::class.java to "ios_greeting",
        IosWorldClock::class.java to "ios_world_clock",
        IosGradientClock::class.java to "ios_gradient_clock",
    )
}

object WidgetUpdater {

    fun update(context: Context, manager: AppWidgetManager, appWidgetId: Int, templateId: String) {
        val template = TemplateCollection.find(context, templateId) ?: return

        val options = manager.getAppWidgetOptions(appWidgetId)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            .takeIf { it > 0 }?.toFloat() ?: TemplateRenderer.nominalDp(template.size.w)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            .takeIf { it > 0 }?.toFloat() ?: TemplateRenderer.nominalDp(template.size.h)

        val bitmap = TemplateRenderer.render(
            template,
            widthDp,
            heightDp,
            context.resources.displayMetrics.density,
            RenderData(ZonedDateTime.now(), BatteryReader.percent(context)),
        )

        val views = RemoteViews(context.packageName, R.layout.widget_root)
        views.setImageViewBitmap(R.id.widget_image, bitmap)
        tapIntent(context, template)?.let { views.setOnClickPendingIntent(R.id.widget_image, it) }
        manager.updateAppWidget(appWidgetId, views)
    }

    // Şimdilik widget'a dokununca şablondaki ilk eylem çalışır.
    private fun tapIntent(context: Context, t: WidgetTemplate): PendingIntent? {
        val action = t.actions.firstOrNull()?.action ?: return null
        val intent = when (action) {
            "open_clock_app" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            "open_calendar" -> Intent(Intent.ACTION_VIEW)
                .setData(Uri.parse("content://com.android.calendar/time/${System.currentTimeMillis()}"))
            "open_battery" -> Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
            "open_subtile" -> Intent(context, MainActivity::class.java)
            else -> return null
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/**
 * Her dakika başı bir sonraki dakikaya alarm kurar ve tüm Subtile widget'larını yeniler.
 * Alarm kesin (exact) değildir, bu yüzden özel izin gerekmez; ekran kapalıyken gecikebilir.
 */
class TickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = AppWidgetManager.getInstance(context)
        var count = 0
        WidgetRegistry.providers.forEach { (cls, templateId) ->
            val ids = manager.getAppWidgetIds(ComponentName(context, cls))
            ids.forEach { WidgetUpdater.update(context, manager, it, templateId) }
            count += ids.size
        }
        if (count > 0) schedule(context)
    }

    companion object {
        fun schedule(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pending = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, TickReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val next = (System.currentTimeMillis() / 60_000L + 1L) * 60_000L
            alarm.setAndAllowWhileIdle(AlarmManager.RTC, next, pending)
        }
    }
}
