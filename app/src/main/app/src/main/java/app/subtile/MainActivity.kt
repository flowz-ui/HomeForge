package app.subtile

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.subtile.template.ParseResult
import app.subtile.template.TemplateParser

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

        // Örnek şablonu okuyup ayrıştırıyoruz. Hata olursa uygulama kapanmaz, ekranda gösterilir.
        val result: ParseResult = try {
            val raw = assets.open("templates/soft_clock.json").bufferedReader().use { it.readText() }
            TemplateParser.parse(raw)
        } catch (e: Throwable) {
            ParseResult.Failure(listOf("${e::class.simpleName}: ${e.message}"))
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text("Subtile", style = MaterialTheme.typography.headlineMedium)
                        when (result) {
                            is ParseResult.Success -> {
                                val t = result.template
                                Text("Şablon yüklendi: ${t.name}")
                                Text("Boyut: ${t.size.w}x${t.size.h}, katman: ${t.layers.size}")
                            }
                            is ParseResult.Failure -> {
                                Text("Şablon hatalı:")
                                result.errors.forEach { Text("• $it") }
                            }
                        }
                    }
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
