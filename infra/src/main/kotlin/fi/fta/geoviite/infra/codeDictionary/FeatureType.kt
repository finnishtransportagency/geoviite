package fi.fta.geoviite.infra.codeDictionary

import fi.fta.geoviite.infra.common.FeatureTypeCode
import fi.fta.geoviite.infra.logging.Loggable
import fi.fta.geoviite.infra.util.FreeText

data class FeatureType(
    val code: FeatureTypeCode,
    val description: FreeText,
) : Loggable {
    override fun toLog() = code.toString()
}
