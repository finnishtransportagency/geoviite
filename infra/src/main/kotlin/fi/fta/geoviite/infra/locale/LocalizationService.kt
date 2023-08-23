package fi.fta.geoviite.infra.locale

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.json.JsonMapper
import fi.fta.geoviite.infra.util.LocalizationKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File

data class Translation(val lang: String, val localization: String) {
    val jsonRoot = JsonMapper().readTree(localization)

    fun t(key: LocalizationKey) = t(key, emptyList())
    fun t(key: String) = t(LocalizationKey(key), emptyList())
    fun t(key: String, params: List<String>) = t(LocalizationKey(key), params)
    fun t(key: LocalizationKey, params: List<String>): String {
        var node = jsonRoot
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
}

class TranslationCache {
    private val translations = mutableMapOf<String, Translation>()
    fun getOrLoadTranslation(lang: String) = translations.getOrPut(lang) {
        Translation(lang, this::class.java.classLoader.getResource("i18n/translations.${lang}.json").readText())
    }
}

@Service
class LocalizationService() {
    val translationCache = TranslationCache()

    @Value("\${geoviite.default-language}")
    val defaultLanguage = ""

    @Value("\${geoviite.i18n.override-path}")
    val overridePath: String = ""

    fun getLocalization(language: String) = if (overridePath.isNotEmpty()) {
        Translation(language, File("${overridePath}translations.${language}.json").readText())
    } else {
        translationCache.getOrLoadTranslation(language)
    }
}
