package fi.fta.geoviite.infra.configuration

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import fi.fta.geoviite.infra.authorization.AuthCode
import fi.fta.geoviite.infra.authorization.AuthName
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.FeatureTypeCode
import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.ProjectName
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geometry.CompanyName
import fi.fta.geoviite.infra.geometry.GeometrySwitchTypeName
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.projektivelho.PVDictionaryCode
import fi.fta.geoviite.infra.projektivelho.PVId
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
import fi.fta.geoviite.infra.util.HttpsUrl
import fi.fta.geoviite.infra.util.UnsafeString
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Configuration
import org.springframework.format.FormatterRegistry
import org.springframework.http.converter.ByteArrayHttpMessageConverter
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@ConditionalOnWebApplication
@EnableWebMvc
@Configuration
class WebConfig(
    @Value("\${geoviite.ext-api.enabled:false}") val extApiEnabled: Boolean,
    @Value("\${geoviite.ext-api.static-url:}") val extApiStaticUrl: String,
    @Value("\${geoviite.ext-api.static-resources:}") val extApiStaticResourcesPath: String,
) : WebMvcConfigurer {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        if (extApiEnabled && extApiStaticUrl.isNotEmpty() && extApiStaticResourcesPath.isNotEmpty()) {
            logger.info("Static file serving enabled, url=$extApiStaticUrl, resources=$extApiStaticResourcesPath")
            registry.addResourceHandler(extApiStaticUrl).addResourceLocations(extApiStaticResourcesPath)
        }
    }

    override fun addFormatters(registry: FormatterRegistry) {
        logger.info("Registering sanitized string converters")
        registry.addStringConstructorConverter(::AuthCode)
        registry.addStringConstructorConverter(::FreeText)
        registry.addStringConstructorConverter(FreeTextWithNewLines::of)
        registry.addStringConstructorConverter(::LocalizationKey)

        registry.addStringConstructorConverter(UserName::of)
        registry.addStringConstructorConverter(AuthName::of)

        registry.addStringConstructorConverter(::CoordinateSystemName)
        registry.addStringConstructorConverter(::FeatureTypeCode)

        registry.addStringConstructorConverter(::UnsafeString)

        logger.info("Registering geometry name converters")
        registry.addStringConstructorConverter(::FileName)
        registry.addStringConstructorConverter(::HttpsUrl)
        registry.addStringConstructorConverter(::MetaDataName)
        registry.addStringConstructorConverter(::CompanyName)
        registry.addStringConstructorConverter(::ProjectName)
        registry.addStringConstructorConverter(::GeometrySwitchTypeName)
        registry.addStringConstructorConverter(::PlanElementName)

        logger.info("Registering layout name converters")
        registry.addStringConstructorConverter(::TrackNumber)
        registry.addStringConstructorConverter(::AlignmentName)
        registry.addStringConstructorConverter(::SwitchName)
        registry.addStringConstructorConverter(::JointNumber)

        logger.info("Registering custom ID converters")
        registry.addStringConstructorConverter { DomainId.parse<Any>(it) }
        registry.addStringConstructorConverter { StringId.parse<Any>(it) }
        registry.addStringConstructorConverter { IntId.parse<Any>(it) }
        registry.addStringConstructorConverter { IndexedId.parse<Any>(it) }

        logger.info("Registering LayoutContext converters")
        registry.addStringConstructorConverter(LayoutBranch::parse)
        registry.addStringConstructorConverter(MainBranch::parse)
        registry.addStringConstructorConverter(DesignBranch::parse)

        logger.info("Registering OID converters")
        registry.addStringConstructorConverter { Oid<Any>(it) }

        logger.info("Registering SRID converters")
        registry.addStringConstructorConverter(::Srid)

        logger.info("Registering version converters")
        registry.addStringConstructorConverter { RowVersion<Any>(it) }
        registry.addStringConstructorConverter { LayoutRowVersion<Any>(it) }

        logger.info("Registering geography converters")
        registry.addStringConstructorConverter(::BoundingBox)
        registry.addStringConstructorConverter(::Point)

        logger.info("Registering track address converters")
        registry.addStringConstructorConverter(::KmNumber)
        registry.addStringConstructorConverter(::TrackMeter)

        logger.info("Registering case-insensitive path variable enum converters")
        registry.addStringConstructorConverter { enumCaseInsensitive<PublicationState>(it) }

        logger.info("Registering ProjektiVelho sanitized string converters")
        registry.addStringConstructorConverter(::PVId)
        registry.addStringConstructorConverter(::PVDictionaryCode)

        logger.info("Registering localization language converters")
        registry.addStringConstructorConverter { enumCaseInsensitive<LocalizationLanguage>(it) }
    }

    override fun configureMessageConverters(converters: MutableList<HttpMessageConverter<*>?>) {
        val builder = Jackson2ObjectMapperBuilder().featuresToDisable(WRITE_DATES_AS_TIMESTAMPS)
        builder.serializationInclusion(JsonInclude.Include.NON_NULL)

        converters.add(ByteArrayHttpMessageConverter())
        converters.add(MappingJackson2HttpMessageConverter(builder.build()))
    }
}

inline fun <reified T : Enum<T>> enumCaseInsensitive(value: String): T = enumValueOf(value.uppercase())

inline fun <reified T> FormatterRegistry.addStringConstructorConverter(noinline initializer: (String) -> T) {
    addConverter(String::class.java, T::class.java, initializer)
    addConverter(T::class.java, String::class.java) { t -> t.toString() }
}
