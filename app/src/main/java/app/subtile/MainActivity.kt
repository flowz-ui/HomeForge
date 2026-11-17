package app.subtile

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.subtile.render.BatteryReader
import app.subtile.render.RenderData
import app.subtile.render.TemplateCollection
import app.subtile.render.TemplateRenderer
import app.subtile.template.ListResult
import app.subtile.template.WidgetTemplate
import java.time.ZonedDateTime

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Çökme kaydı: uygulama çökerse hata kaydedilir ve bir sonraki açılışta ekranda gösterilir.
        val prefs = getSharedPreferences("crash", MODE_PRIVATE)
        val lastCrash = prefs.getString("last", null)
        if (lastCrash != null) {
            prefs.edit().remove("last").commit()
            showCrash(lastCrash)
            return
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                prefs.edit().putString("last", Log.getStackTraceString(error).take(3500)).commit()
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, error)
        }

        val collection: ListResult = try {
            TemplateCollection.loadWithErrors(this)
        } catch (e: Throwable) {
            ListResult(emptyList(), listOf("${e::class.simpleName}: ${e.message}"))
        }
        val battery = BatteryReader.percent(this)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Home(collection, battery)
                }
            }
        }
    }

    private fun showCrash(text: String) {
        val tv = TextView(this).apply {
            this.text = "Subtile çöktü. Bu ekranın görüntüsünü gönder:\n\n$text"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(32, 96, 32, 32)
            setTextIsSelectable(true)
        }
        val sv = ScrollView(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                tv,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        setContentView(sv)
    }
}

@Composable
private fun Home(collection: ListResult, battery: Int) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 48.dp),
    ) {
        item {
            Text("Subtile", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Ana ekranda boş bir yere uzun bas, Widget'lar'a gir ve Subtile'ı seç.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))
        }
        if (collection.errors.isNotEmpty()) {
            item {
                Text("Hatalı şablonlar:", style = MaterialTheme.typography.titleMedium)
                collection.errors.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                Spacer(Modifier.height(24.dp))
            }
        }
        items(collection.templates) { template ->
            Preview(template, battery)
        }
    }
}

@Composable
private fun Preview(template: WidgetTemplate, battery: Int) {
    val density = LocalDensity.current.density
    val widthDp = TemplateRenderer.nominalDp(template.size.w)
    val heightDp = TemplateRenderer.nominalDp(template.size.h)
    val bitmap = remember(template, density) {
        TemplateRenderer.render(
            template, widthDp, heightDp, density,
            RenderData(ZonedDateTime.now(), battery),
        ).asImageBitmap()
    }
    Column {
        Text(template.name, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        Image(
            bitmap = bitmap,
            contentDescription = template.name,
            modifier = Modifier.size(widthDp.dp, heightDp.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}
