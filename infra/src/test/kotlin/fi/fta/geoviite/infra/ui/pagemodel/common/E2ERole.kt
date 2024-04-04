package fi.fta.geoviite.infra.ui.pagemodel.common

enum class E2ERole(val roleCode: String) {
    Operator("operator"),
    Team("team"),
    Authority("authority"),
    Consultant("consultant"),
    Browser("browser");

    override fun toString(): String {
        return roleCode
    }
}
