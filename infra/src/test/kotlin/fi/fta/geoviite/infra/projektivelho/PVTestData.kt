package fi.fta.geoviite.infra.projektivelho

val materialDictionaries: Map<PVDictionaryType, List<PVDictionaryEntry>> =
    mapOf(
        PVDictionaryType.DOCUMENT_TYPE to
            listOf(
                PVDictionaryEntry("dokumenttityyppi/dt01", "test doc type 1"),
                PVDictionaryEntry("dokumenttityyppi/dt02", "test doc type 2"),
            ),
        PVDictionaryType.MATERIAL_STATE to
            listOf(
                PVDictionaryEntry("aineistotila/tila01", "test mat state 1"),
                PVDictionaryEntry("aineistotila/tila02", "test mat state 2"),
            ),
        PVDictionaryType.MATERIAL_CATEGORY to
            listOf(
                PVDictionaryEntry("aineistolaji/al00", "test mat category 0"),
                PVDictionaryEntry("aineistolaji/al01", "test mat category 1"),
            ),
        PVDictionaryType.MATERIAL_GROUP to
            listOf(
                PVDictionaryEntry("aineistoryhma/ar00", "test mat group 0"),
                PVDictionaryEntry("aineistoryhma/ar01", "test mat group 1"),
            ),
        PVDictionaryType.TECHNICS_FIELD to
            listOf(
                PVDictionaryEntry("tekniikka-ala/ta00", "test tech field 0"),
                PVDictionaryEntry("tekniikka-ala/ta01", "test tech field 1"),
            ),
    )

val projectDictionaries: Map<PVDictionaryType, List<PVDictionaryEntry>> =
    mapOf(
        PVDictionaryType.PROJECT_STATE to
            listOf(
                PVDictionaryEntry("tila/tila14", "test state 14"),
                PVDictionaryEntry("tila/tila15", "test state 15"),
            ),
    )
