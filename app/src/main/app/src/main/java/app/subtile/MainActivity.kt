package app.subtile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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

        // Adım 2 doğrulaması: örnek şablonu okuyup ayrıştırıyoruz. Renderer Adım 3'te geliyor.
        val raw = assets.open("templates/soft_clock.json").bufferedReader().use { it.readText() }
        val result = TemplateParser.parse(raw)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.padding(24.dp)) {
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
