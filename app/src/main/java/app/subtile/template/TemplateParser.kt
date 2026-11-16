package app.subtile.template

import kotlinx.serialization.json.Json

sealed interface ParseResult {
    data class Success(val template: WidgetTemplate) : ParseResult
    data class Failure(val errors: List<String>) : ParseResult
}

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

    fun toJson(template: WidgetTemplate): String =
        json.encodeToString(WidgetTemplate.serializer(), template)
}
