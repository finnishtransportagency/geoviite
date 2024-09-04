package fi.fta.geoviite.infra.projektivelho

val materialDictionaries: Map<PVDictionaryType, List<PVApiDictionaryEntry>> =
    mapOf(
        PVDictionaryType.DOCUMENT_TYPE to
            listOf(
                PVApiDictionaryEntry("dokumenttityyppi/dt01", "test doc type 1"),
                PVApiDictionaryEntry("dokumenttityyppi/dt02", "test doc type 2"),
            ),
        PVDictionaryType.MATERIAL_STATE to
            listOf(
                PVApiDictionaryEntry("aineistotila/tila01", "test mat state 1"),
                PVApiDictionaryEntry("aineistotila/tila02", "test mat state 2"),
            ),
        PVDictionaryType.MATERIAL_CATEGORY to
            listOf(
                PVApiDictionaryEntry("aineistolaji/al00", "test mat category 0"),
                PVApiDictionaryEntry("aineistolaji/al01", "test mat category 1"),
            ),
        PVDictionaryType.MATERIAL_GROUP to
            listOf(
                PVApiDictionaryEntry("aineistoryhma/ar00", "test mat group 0"),
                PVApiDictionaryEntry("aineistoryhma/ar01", "test mat group 1"),
            ),
        PVDictionaryType.TECHNICS_FIELD to
            listOf(
                PVApiDictionaryEntry("tekniikka-ala/ta00", "test tech field 0"),
                PVApiDictionaryEntry("tekniikka-ala/ta01", "test tech field 1"),
            ),
    )

val projectDictionaries: Map<PVDictionaryType, List<PVApiDictionaryEntry>> =
    mapOf(
        PVDictionaryType.PROJECT_STATE to
            listOf(
                PVApiDictionaryEntry("tila/tila14", "test state 14"),
                PVApiDictionaryEntry("tila/tila15", "test state 15"),
            )
    )
