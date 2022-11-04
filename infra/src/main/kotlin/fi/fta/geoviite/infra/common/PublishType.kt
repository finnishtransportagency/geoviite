package fi.fta.geoviite.infra.common

import org.springframework.core.convert.converter.Converter

enum class PublishType {
    OFFICIAL,
    DRAFT
}

class StringToPublishTypeConverter : Converter<String, PublishType> {
    override fun convert(source: String) = PublishType.valueOf(source.uppercase())
}
