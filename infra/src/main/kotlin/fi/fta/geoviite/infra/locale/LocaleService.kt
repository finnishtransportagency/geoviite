package fi.fta.geoviite.infra.locale

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import fi.fta.geoviite.infra.util.LocalizationKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

class TranslationCache {
    val translations = mutableMapOf<String, String>()
    fun getOrLoadTranslation(lang: String): String {
        return translations.getOrPut(lang) {
            this::class.java.classLoader.getResource("i18n/translations.${lang}.json").readText()
        }
    }
}

@Service
class LocaleService {
    val outboundTranslationMapper = ObjectMapper()
    val jsonMapper = JsonMapper()
    val translations = TranslationCache()

    @Value("\${geoviite.default-language}")
    var language = ""

    fun getLocale(language: String): Any {
        return translations.getOrLoadTranslation(language).let { translation ->
            outboundTranslationMapper.readValue(translation, Any::class.java)
        }
    }

    fun t(key: LocalizationKey) = t(key, emptyList())
    fun t(key: String) = t(LocalizationKey(key), emptyList())
    fun t(key: String, params: List<String>) = t(LocalizationKey(key), params)
    fun t(key: LocalizationKey, params: List<String>): String {
        var node = jsonMapper.readTree(translations.getOrLoadTranslation(language))
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
