package app.subtile.template

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

sealed interface ParseResult {
    data class Success(val template: WidgetTemplate) : ParseResult
    data class Failure(val errors: List<String>) : ParseResult
}

/** Geçerli şablonlar ve atlanan şablonların hataları. */
data class ListResult(val templates: List<WidgetTemplate>, val errors: List<String>)

object TemplateParser {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun parse(text: String): ParseResult {
        val template = try {
            json.decodeFromString(WidgetTemplate.serializer(), text)
        } catch (e: Exception) {
            return ParseResult.Failure(listOf("JSON okunamadı: ${e.message}"))
        }
        val errors = TemplateValidator.validate(template)
        return if (errors.isEmpty()) ParseResult.Success(template) else ParseResult.Failure(errors)
    }

    /** Birden fazla şablon içeren JSON dizisini okur. Hatalı şablonlar atlanır, hataları döner. */
    fun parseList(text: String): ListResult {
        val all = try {
            json.decodeFromString(ListSerializer(WidgetTemplate.serializer()), text)
        } catch (e: Exception) {
            return ListResult(emptyList(), listOf("JSON okunamadı: ${e.message}"))
        }
        val ok = mutableListOf<WidgetTemplate>()
        val errors = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        all.forEachIndexed { i, t ->
            val label = t.name.ifBlank { "#${i + 1}" }
            val problems = TemplateValidator.validate(t).toMutableList()
            if (t.id.isEmpty()) problems += "id boş"
            if (t.id.isNotEmpty() && !seen.add(t.id)) problems += "id tekrar ediyor: ${t.id}"
            if (problems.isEmpty()) ok += t else errors += problems.map { "$label: $it" }
        }
        return ListResult(ok, errors)
    }

    fun toJson(template: WidgetTemplate): String =
        json.encodeToString(WidgetTemplate.serializer(), template)
}
