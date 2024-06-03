package fi.fta.geoviite.infra.localization

enum class LocalizationLanguage {
    FI,
    EN;

    fun lowercase() = name.lowercase()
}
