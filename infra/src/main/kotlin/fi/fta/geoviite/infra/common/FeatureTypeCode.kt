package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized

private val featureTypeCodeLength = 3..3
private val featureTypeCodeRegex = Regex("^\\d{3}\$")

data class FeatureTypeCode @JsonCreator(mode = DELEGATING) constructor(private val value: String)
    : CharSequence by value {
    init { assertSanitized<FeatureTypeCode>(value, featureTypeCodeRegex, featureTypeCodeLength) }

    @JsonValue
    override fun toString(): String = value
}
