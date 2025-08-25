package fi.fta.geoviite.infra.localization

import com.fasterxml.jackson.databind.json.JsonMapper
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.util.FileName
import org.springframework.beans.factory.annotation.Value
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val LOCALIZATION_PARAMS_PLACEHOLDER_REGEX = Regex("\\{\\{[a-zA-Z0-9_\\s\\-]*\\}\\}")

data class Translation(val lang: LocalizationLanguage, val localization: String) {
    private val jsonRoot = JsonMapper().readTree(localization)
    private val nodeTextCache = ConcurrentHashMap<LocalizationKey, String>()

    fun t(key: LocalizationKey) = t(key, LocalizationParams.empty)

    fun t(key: String) = t(LocalizationKey.of(key), LocalizationParams.empty)

    fun t(key: String, params: LocalizationParams) = t(LocalizationKey.of(key), params)

    fun t(key: LocalizationKey, params: LocalizationParams): String =
        getNodeText(key).replace(LOCALIZATION_PARAMS_PLACEHOLDER_REGEX) { prop ->
            val propKey = prop.value.substring(2, prop.value.length - 2).trim()
            params.get(propKey)
        }

    private fun getNodeText(key: LocalizationKey) =
        nodeTextCache.computeIfAbsent(key) { k ->
            var node = jsonRoot
            k.split(".").let { keyPart -> keyPart.forEach { part -> node = node.path(part) } }
            if (node.isMissingNode) key.toString() else node.asText()
        }

    fun filename(key: String, params: LocalizationParams, branch: String = "filename"): FileName {
        return t("$branch.$key", params).let(::FileName)
    }

    inline fun <reified T : Enum<*>> enum(variant: T, branch: String = "enum", lowercase: Boolean = false): String {
        val translatedVariant = t("$branch.${T::class.simpleName}.$variant")

        return if (lowercase) {
            translatedVariant.lowercase()
        } else {
            translatedVariant
        }
    }
}

class TranslationCache {
    private val translations = ConcurrentHashMap<LocalizationLanguage, Translation>()

    fun getOrLoadTranslation(lang: LocalizationLanguage): Translation =
        translations.computeIfAbsent(lang) {
            this::class.java.classLoader.getResource("i18n/translations.${lang.lowercase()}.json").let {
                Translation(lang, it?.readText() ?: "")
            }
        }
}

@GeoviiteService
class LocalizationService(@Value("\${geoviite.i18n.override-path:}") val overridePath: String = "") {
    val translationCache = TranslationCache()

    fun getLocalization(language: LocalizationLanguage): Translation =
        if (overridePath.isNotEmpty()) {
            Translation(language, File("${overridePath}translations.${language.lowercase()}.json").readText())
        } else {
            translationCache.getOrLoadTranslation(language)
        }
}
