package fi.fta.geoviite.infra.localization

import com.fasterxml.jackson.databind.json.JsonMapper
import fi.fta.geoviite.infra.error.LocalizationParams
import fi.fta.geoviite.infra.util.LocalizationKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.ConcurrentHashMap

data class Translation(val lang: String, val localization: String) {
    private val jsonRoot = JsonMapper().readTree(localization)

    fun t(key: LocalizationKey) = t(key, emptyMap())
    fun t(key: String) = t(LocalizationKey(key), emptyMap())
    fun t(key: String, params: LocalizationParams) = t(LocalizationKey(key), params)
    fun t(key: LocalizationKey, params: LocalizationParams): String {
        var node = jsonRoot
        key.split(".").let { keyPart ->
            keyPart.forEach { part ->
                node = node.path(part)
            }
        }

        val nodeText = if (node.isMissingNode) key.toString() else node.asText()
        return nodeText.replace(Regex("\\{\\{[a-zA-Z0-9]*\\}\\}")) {
            val propIndex = it.value.substring(2, it.value.length - 2).trim()
            params[propIndex] ?: ""
        }
    }
}

class TranslationCache {
    private val translations = ConcurrentHashMap<String, Translation>()
    fun getOrLoadTranslation(lang: String): Translation = translations.getOrPut(lang) {
        this::class.java.classLoader.getResource("i18n/translations.${lang}.json")
            .let { Translation(lang, it?.readText() ?: "") }
    }
}

@Service
class LocalizationService(@Value("\${geoviite.i18n.override-path:}") val overridePath: String = "") {
    val translationCache = TranslationCache()

    fun getLocalization(language: String): Translation = if (overridePath.isNotEmpty()) {
        Translation(language, File("${overridePath}translations.${language}.json").readText())
    } else {
        translationCache.getOrLoadTranslation(language)
    }
}
