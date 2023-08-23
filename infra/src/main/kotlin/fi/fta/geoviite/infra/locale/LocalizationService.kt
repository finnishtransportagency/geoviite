package fi.fta.geoviite.infra.locale

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
import fi.fta.geoviite.infra.util.LocalizationKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File

enum class SupportedLanguage(val locale: String) {
    FI("fi"), EN("en"),
}

data class Translation(val lang: SupportedLanguage, val localization: JsonNode)

class TranslationCache(val retentionTime: Long?, val getter: (String) -> String) {
    private val translations = mutableMapOf<String, String>()
    fun getOrLoadTranslation(lang: String): String = translations.getOrPut(lang) {
        this::class.java.classLoader.getResource("i18n/translations.${lang}.json").readText()
    }
}

@Service
class LocalizationService() {
    val translationCache by lazy {
        TranslationCache(if (overridePath.isNotEmpty()) 10000 else null) { lang ->
            if (overridePath.isNotEmpty()) {
                File("${overridePath}translations.${lang}.json").readText()
            } else {
                this::class.java.classLoader.getResource("i18n/translations.${lang}.json").readText()
            }
        }
    }
    val jsonMapper = JsonMapper()

    @Value("\${geoviite.default-language}")
    val defaultLanguage = ""

    @Value("\${geoviite.i18n.override-path}")
    val overridePath: String = ""

    fun getLocalization(language: String) = if (overridePath.isNotEmpty()) {
        File("${overridePath}translations.${language}.json").readText()
    } else {
        translationCache.getOrLoadTranslation(language)
    }

    fun getLocalizationJson(language: String) = jsonMapper.readTree(getLocalization(language))
}

fun t(translations: JsonNode, key: LocalizationKey) = t(translations, key, emptyList())
fun t(translations: JsonNode, key: String) = t(translations, LocalizationKey(key), emptyList())
fun t(translations: JsonNode, key: String, params: List<String>) = t(translations, LocalizationKey(key), params)
fun t(translations: JsonNode, key: LocalizationKey, params: List<String>): String {
    var node = translations
    key.split(".").let { keyPart ->
        keyPart.forEach { part ->
            node = node.path(part)
        }
    }

    val nodeText = if (node.isMissingNode) key.toString() else node.asText()
    return nodeText.replace(Regex("\\{\\{[0-9]*\\}\\}")) {
        val propIndex = it.value.substring(2, it.value.length - 2).trim().toInt()
        params.getOrNull(propIndex) ?: ""
    }
}
