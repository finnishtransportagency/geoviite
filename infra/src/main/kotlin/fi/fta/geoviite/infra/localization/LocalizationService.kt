package fi.fta.geoviite.infra.localization

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.json.JsonMapper
import fi.fta.geoviite.infra.util.LocalizationKey
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val LOCALIZATION_PARAMS_KEY_REGEX = Regex("[a-zA-Z0-9_\\s\\-]*")
private val LOCALIZATION_PARAMS_PLACEHOLDER_REGEX = Regex("\\{\\{[a-zA-Z0-9_\\s\\-]*\\}\\}")

data class LocalizationParams @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
constructor(@JsonValue val params: Map<String, Any?>) {
    constructor(vararg params: Pair<String, Any?>) : this(mapOf(*params))

    init {
        require(params.none { LOCALIZATION_PARAMS_KEY_REGEX.matchEntire(it.key) == null }) {
            "There are localization keys that do not match with the localization pattern regex. Keys in question are ${
                params.map { it.key }.filter { LOCALIZATION_PARAMS_KEY_REGEX.matchEntire(it) == null }
            }"
        }
    }

    fun get(key: String) = params[key]?.toString() ?: "" //Null values are treated as empty strings instead of "null"

    companion object {
        fun empty() = LocalizationParams()
    }
}

data class Translation(val lang: String, val localization: String) {
    private val jsonRoot = JsonMapper().readTree(localization)

    fun t(key: LocalizationKey) = t(key, LocalizationParams.empty())
    fun t(key: String) = t(LocalizationKey(key), LocalizationParams.empty())
    fun t(key: String, params: LocalizationParams) = t(LocalizationKey(key), params)
    fun t(key: LocalizationKey, params: LocalizationParams): String {
        var node = jsonRoot
        key.split(".").let { keyPart ->
            keyPart.forEach { part ->
                node = node.path(part)
            }
        }

        val nodeText = if (node.isMissingNode) key.toString() else node.asText()
        return nodeText.replace(LOCALIZATION_PARAMS_PLACEHOLDER_REGEX) {
            val propKey = it.value.substring(2, it.value.length - 2).trim()
            params.get(propKey)
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
