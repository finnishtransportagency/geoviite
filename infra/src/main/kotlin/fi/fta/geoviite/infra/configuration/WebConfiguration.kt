package fi.fta.geoviite.infra.configuration

import com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
import fi.fta.geoviite.infra.authorization.AuthNameToStringConverter
import fi.fta.geoviite.infra.authorization.StringToAuthNameConverter
import fi.fta.geoviite.infra.authorization.StringToUserNameConverter
import fi.fta.geoviite.infra.authorization.UserNameToStringConverter
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.CoordinateSystemNameToStringConverter
import fi.fta.geoviite.infra.geography.StringToCoordinateSystemNameConverter
import fi.fta.geoviite.infra.geometry.GeometrySwitchTypeNameToStringConverter
import fi.fta.geoviite.infra.geometry.MetaDataNameToStringConverter
import fi.fta.geoviite.infra.geometry.StringToGeometrySwitchTypeNameConverter
import fi.fta.geoviite.infra.geometry.StringToMetaDataNameConverter
import fi.fta.geoviite.infra.inframodel.PlanElementNameToStringConverter
import fi.fta.geoviite.infra.inframodel.StringToPlanElementNameConverter
import fi.fta.geoviite.infra.math.BoundingBoxToStringConverter
import fi.fta.geoviite.infra.math.StringToBoundingBoxConverter
import fi.fta.geoviite.infra.math.StringToPointConverter
import fi.fta.geoviite.infra.util.*
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
        registry.addConverter(StringToCodeConverter())
        registry.addConverter(CodeToStringConverter())
        registry.addConverter(StringToDescriptionConverter())
        registry.addConverter(DescriptionToStringConverter())

        registry.addConverter(StringToLocalizationKeyConverter())
        registry.addConverter(LocalizationKeyToStringConverter())

        registry.addConverter(StringToCoordinateSystemNameConverter())
        registry.addConverter(CoordinateSystemNameToStringConverter())

        registry.addConverter(UserNameToStringConverter())
        registry.addConverter(StringToUserNameConverter())
        registry.addConverter(StringToAuthNameConverter())
        registry.addConverter(AuthNameToStringConverter())
        registry.addConverter(FeatureTypeCodeToStringConverter())
        registry.addConverter(StringToFeatureTypeCodeConverter())

        logger.info("Registering geometry name converters")
        registry.addConverter(StringToFileNameConverter())
        registry.addConverter(FileNameToStringConverter())
        registry.addConverter(StringToMetaDataNameConverter())
        registry.addConverter(MetaDataNameToStringConverter())
        registry.addConverter(StringToGeometrySwitchTypeNameConverter())
        registry.addConverter(GeometrySwitchTypeNameToStringConverter())
        registry.addConverter(StringToPlanElementNameConverter())
        registry.addConverter(PlanElementNameToStringConverter())

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
        registry.addConverter(StringToOidConverter<Any>())
        registry.addConverter(OidToStringConverter<Any>())

        logger.info("Registering SRID converters")
        registry.addConverter(SridToStringConverter())
        registry.addConverter(StringToSridConverter())

        logger.info("Registering version converters")
        registry.addConverter(VersionToStringConverter())
        registry.addConverter(StringToVersionConverter())

        logger.info("Registering bounding box converters")
        registry.addConverter(BoundingBoxToStringConverter())
        registry.addConverter(StringToBoundingBoxConverter())

        logger.info("Registering point converters")
        registry.addConverter(StringToPointConverter())

        logger.info("Registering layout name converters")
        registry.addConverter(StringToTrackNumberConverter())
        registry.addConverter(TrackNumberToStringConverter())
        registry.addConverter(AlignmentNameToStringConverter())
        registry.addConverter(StringToAlignmentNameConverter())
        registry.addConverter(SwitchNameToStringConverter())
        registry.addConverter(StringToSwitchNameConverter())

        logger.info("Registering track km+meter converters")
        registry.addConverter(StringToKmNumberConverter())
        registry.addConverter(KmNumberToStringConverter())
        registry.addConverter(StringToTrackMeterConverter())
        registry.addConverter(TrackMeterToStringConverter())

        logger.info("Registering joint number converters")
        registry.addConverter(StringToJointNumberConverter())
        registry.addConverter(JointNumberToStringConverter())

        logger.info("Registering case-insensitive path variable enum converters")
        registry.addConverter(StringToPublishTypeConverter())
    }

    override fun configureMessageConverters(converters: MutableList<HttpMessageConverter<*>?>) {
        val builder = Jackson2ObjectMapperBuilder().featuresToDisable(WRITE_DATES_AS_TIMESTAMPS)
        converters.add(MappingJackson2HttpMessageConverter(builder.build()))
        converters.add(ByteArrayHttpMessageConverter())
    }
}
