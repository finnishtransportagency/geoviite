package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized
import org.springframework.core.convert.converter.Converter

private val featureTypeCodeLength = 3..3
private val featureTypeCodeRegex = Regex("^\\d{3}\$")

data class FeatureTypeCode @JsonCreator(mode = DELEGATING) constructor(val value: String)
    : CharSequence by value {

    @JsonValue
    override fun toString(): String = value

    init { assertSanitized<FeatureTypeCode>(value, featureTypeCodeRegex, featureTypeCodeLength) }
}

class StringToFeatureTypeCodeConverter : Converter<String, FeatureTypeCode> {
    override fun convert(source: String): FeatureTypeCode = FeatureTypeCode(source)
}

class FeatureTypeCodeToStringConverter : Converter<FeatureTypeCode, String> {
    override fun convert(source: FeatureTypeCode): String = source.value
}
