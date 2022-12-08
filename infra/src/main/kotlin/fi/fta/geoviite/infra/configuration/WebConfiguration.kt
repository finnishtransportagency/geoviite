package fi.fta.geoviite.infra.configuration

import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import fi.fta.geoviite.infra.authorization.AuthName
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geometry.GeometrySwitchTypeName
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.util.Code
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.LocalizationKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Configuration
import org.springframework.format.FormatterRegistry
import org.springframework.http.converter.ByteArrayHttpMessageConverter
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@ConditionalOnWebApplication
@EnableWebMvc
@Configuration
class WebConfig : WebMvcConfigurer {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun addFormatters(registry: FormatterRegistry) {
        logger.info("Registering sanitized string converters")
        registry.addStringConstructorConverter(::Code)
        registry.addStringConstructorConverter(::FreeText)
        registry.addStringConstructorConverter(::LocalizationKey)

        registry.addStringConstructorConverter(::UserName)
        registry.addStringConstructorConverter(::AuthName)

        registry.addStringConstructorConverter(::CoordinateSystemName)
        registry.addStringConstructorConverter(::FeatureTypeCode)

        logger.info("Registering geometry name converters")
        registry.addStringConstructorConverter(::FileName)
        registry.addStringConstructorConverter(::MetaDataName)
        registry.addStringConstructorConverter(::GeometrySwitchTypeName)
        registry.addStringConstructorConverter(::PlanElementName)

        logger.info("Registering layout name converters")
        registry.addStringConstructorConverter(::TrackNumber)
        registry.addStringConstructorConverter(::AlignmentName)
        registry.addStringConstructorConverter(::SwitchName)
        registry.addStringConstructorConverter(::JointNumber)

        logger.info("Registering custom ID converters")
        registry.addConverter(StringToDomainIdConverter<Any>())
        registry.addConverter(DomainIdToStringConverter<Any>())
        registry.addConverter(StringToStringIdConverter<Any>())
        registry.addConverter(StringIdToStringConverter<Any>())
        registry.addConverter(StringToIntIdConverter<Any>())
        registry.addConverter(IntIdToStringConverter<Any>())
        registry.addConverter(StringToIndexedIdConverter<Any>())
        registry.addConverter(IndexedIdToStringConverter<Any>())

        logger.info("Registering OID converters")
        registry.addStringConstructorConverter { Oid<Any>(it) }

        logger.info("Registering SRID converters")
        registry.addStringConstructorConverter(::Srid)

        logger.info("Registering version converters")
        registry.addStringConstructorConverter { RowVersion<Any>(it) }

        logger.info("Registering geography converters")
        registry.addStringConstructorConverter(::BoundingBox)
        registry.addStringConstructorConverter(::Point)

        logger.info("Registering track address converters")
        registry.addStringConstructorConverter(::KmNumber)
        registry.addStringConstructorConverter(::TrackMeter)

        logger.info("Registering case-insensitive path variable enum converters")
        registry.addStringConstructorConverter { enumCaseInsensitive<PublishType>(it) }
    }

    override fun configureMessageConverters(converters: MutableList<HttpMessageConverter<*>?>) {
        val builder = Jackson2ObjectMapperBuilder().featuresToDisable(WRITE_DATES_AS_TIMESTAMPS)
        converters.add(MappingJackson2HttpMessageConverter(builder.build()))
        converters.add(ByteArrayHttpMessageConverter())
    }
}

inline fun <reified T : Enum<T>> enumCaseInsensitive(value: String): T = enumValueOf(value.uppercase())

inline fun <reified T> FormatterRegistry.addStringConstructorConverter(noinline initializer: (String) -> T) {
    addConverter(String::class.java, T::class.java, initializer)
    addConverter(T::class.java, String::class.java) { t -> t.toString() }
}
