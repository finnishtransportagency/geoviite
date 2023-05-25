import PVDictionaryGroup.MATERIAL
import PVDictionaryGroup.PROJECT

enum class PVDictionaryGroup {
    MATERIAL,
    PROJECT,
}

enum class PVDictionaryType(val group: PVDictionaryGroup) {
    DOCUMENT_TYPE (MATERIAL), // dokumenttityyppi
    MATERIAL_STATE (MATERIAL), // aineistotila
    MATERIAL_CATEGORY (MATERIAL), // aineistolaji
    MATERIAL_GROUP (MATERIAL), // ainestoryhmä
    TECHNICS_FIELD (MATERIAL), // teknikka-ala
    PROJECT_STATE (PROJECT), // projektin tila
}

data class PVDictionaryEntry(val code: PVCode, val name: PVName) {
    constructor(code: String, name: String): this(PVCode(code), PVName(name))
}

