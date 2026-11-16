package app.subtile.template

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
        val result = TemplateParser.parse(bad)
        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun rejectsUnknownAction() {
        val bad = valid.replace("open_clock_app", "run_script")
        val result = TemplateParser.parse(bad)
        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun rejectsOutOfRangePosition() {
        val bad = valid.replace("\"x\": 0.82", "\"x\": 1.8")
        val result = TemplateParser.parse(bad)
        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun rejectsBrokenJson() {
        val result = TemplateParser.parse("{ not json")
        assertTrue(result is ParseResult.Failure)
    }

    @Test
    fun roundTripKeepsTemplate() {
        val first = (TemplateParser.parse(valid) as ParseResult.Success).template
        val second = (TemplateParser.parse(TemplateParser.toJson(first)) as ParseResult.Success).template
        assertEquals(first, second)
    }
}
