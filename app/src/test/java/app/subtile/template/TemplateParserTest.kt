package app.subtile.template

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateParserTest {

    private val valid = """
        {
          "schema": 1,
          "name": "Soft Clock",
          "size": { "w": 4, "h": 2 },
          "background": { "type": "gradient", "colors": ["#1B1F3B", "#6A4C93"], "angle": 135, "radius": 28 },
          "layers": [
            { "type": "text", "source": "time", "format": "HH:mm", "x": 0.08, "y": 0.2, "size": 56, "color": "#FFFFFF" },
            { "type": "battery", "style": "ring", "x": 0.82, "y": 0.2, "size": 40, "color": "#FF9F1C" }
          ],
          "actions": [ { "area": [0, 0, 1, 1], "do": "open_clock_app" } ]
        }
    """.trimIndent()

    @Test
    fun parsesValidTemplate() {
        val result = TemplateParser.parse(valid)
        assertTrue(result is ParseResult.Success)
        val t = (result as ParseResult.Success).template
        assertEquals("Soft Clock", t.name)
        assertEquals(2, t.layers.size)
        assertTrue(t.layers[0] is Layer.Text)
        assertTrue(t.layers[1] is Layer.Battery)
    }

    @Test
    fun rejectsBadColor() {
        val bad = valid.replace("#FF9F1C", "orange")
        assertTrue(TemplateParser.parse(bad) is ParseResult.Failure)
    }

    @Test
    fun rejectsUnknownAction() {
        val bad = valid.replace("open_clock_app", "run_script")
        assertTrue(TemplateParser.parse(bad) is ParseResult.Failure)
    }

    @Test
    fun rejectsOutOfRangePosition() {
        val bad = valid.replace("\"x\": 0.82", "\"x\": 1.8")
        assertTrue(TemplateParser.parse(bad) is ParseResult.Failure)
    }

    @Test
    fun rejectsBrokenJson() {
        assertTrue(TemplateParser.parse("{ not json") is ParseResult.Failure)
    }

    @Test
    fun rejectsBadTimeZone() {
        val bad = valid.replace("\"format\": \"HH:mm\"", "\"format\": \"HH:mm\", \"tz\": \"Mars/Olympus\"")
        assertTrue(TemplateParser.parse(bad) is ParseResult.Failure)
    }

    @Test
    fun rejectsBadDateFormat() {
        val bad = valid.replace("\"format\": \"HH:mm\"", "\"format\": \"HH:mm {{\"")
        assertTrue(TemplateParser.parse(bad) is ParseResult.Failure)
    }

    @Test
    fun roundTripKeepsTemplate() {
        val first = (TemplateParser.parse(valid) as ParseResult.Success).template
        val second = (TemplateParser.parse(TemplateParser.toJson(first)) as ParseResult.Success).template
        assertEquals(first, second)
    }

    /** Resmi koleksiyondaki her şablon geçerli olmalı. Bozuk bir şablon derlemeyi kırmızı yapar. */
    @Test
    fun officialCollectionIsValid() {
        val file = listOf(
            File("src/main/assets/templates/collection.json"),
            File("app/src/main/assets/templates/collection.json"),
        ).first { it.exists() }
        val result = TemplateParser.parseList(file.readText())
        assertEquals("Hatalı şablonlar: ${result.errors}", emptyList<String>(), result.errors)
        assertEquals(14, result.templates.size)
    }
}
