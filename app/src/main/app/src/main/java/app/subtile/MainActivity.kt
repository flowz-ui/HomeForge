package app.subtile

import android.os.Bundle
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
}
