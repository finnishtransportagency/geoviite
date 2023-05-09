package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.InframodelParsingException
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geometry.ErrorType
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.PlanSource
import fi.fta.geoviite.infra.geometry.ValidationError
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchType
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.LocalizationKey
import fi.fta.geoviite.infra.util.xmlBytesToString
import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.Marshaller
import jakarta.xml.bind.UnmarshalException
import jakarta.xml.bind.Unmarshaller
import org.springframework.http.MediaType
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.nio.charset.Charset
import javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.sax.SAXSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory

// Schema from https://buildingsmart.fi/infra/schema/im_current.html
private const val SCHEMA_LOCATION = "/xml/inframodel.xsd"

const val INFRAMODEL_PARSING_KEY_PARENT = "error.infra-model.parsing"
const val INFRAMODEL_PARSING_KEY_GENERIC = "$INFRAMODEL_PARSING_KEY_PARENT.generic"

data class ParsingError(override val localizationKey: LocalizationKey) : ValidationError {
    constructor(key: String) : this(LocalizationKey(key))
    override val errorType = ErrorType.PARSING_ERROR
}

private val jaxbContext: JAXBContext by lazy {
    JAXBContext.newInstance(
        InfraModel403::class.java,
        InfraModel404::class.java
    )
}

private val schema: Schema by lazy {
    val language = W3C_XML_SCHEMA_NS_URI
    val factory = SchemaFactory.newInstance(language)
    factory.newSchema(
        InfraModel403::class.java.getResource(SCHEMA_LOCATION)
            ?: throw IllegalArgumentException("Failed to load schema from classpath:$SCHEMA_LOCATION")
    )
}


val unmarshaller: Unmarshaller by lazy { jaxbContext.createUnmarshaller() }

val marshaller: Marshaller by lazy { jaxbContext.createMarshaller() }

private val saxParserFactory: SAXParserFactory by lazy {
    val spf = SAXParserFactory.newInstance()

    // Validate
    spf.isNamespaceAware = true
    // This id only DTD validation, as per http://www.w3.org/TR/REC-xml#proc-types - we don't want that
    spf.isValidating = false
    spf.schema = schema

    // XEE prevention, According to:
    // https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html

    // Disable DTDs
    spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)

    // Disable XXE
    spf.setFeature("http://xml.org/sax/features/external-general-entities", false)
    spf.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    spf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

    spf
}

fun toSaxSource(xmlString: String) = SAXSource(
    saxParserFactory.newSAXParser().xmlReader,
    InputSource(StringReader(xmlString)),
)

fun parseGeometryPlan(
    source: PlanSource,
    file: File,
    fileName: FileName = FileName(file.name),
    coordinateSystems: Map<CoordinateSystemName, Srid> = mapOf(),
    switchStructuresByType: Map<SwitchType, SwitchStructure>,
    switchTypeNameAliases: Map<String, String>,
    trackNumberIdsByNumber: Map<TrackNumber, IntId<TrackLayoutTrackNumber>>,
): Pair<GeometryPlan, InfraModelFile> {
    val imFile = toInfraModelFile(fileName, fileToString(file))
    return parseInfraModelFile(
        source,
        imFile,
        coordinateSystems,
        switchStructuresByType,
        switchTypeNameAliases,
        trackNumberIdsByNumber
    ) to imFile
}

fun parseGeometryPlan(
    source: PlanSource,
    file: ByteArray,
    fileName: FileName,
    fileEncodingOverride: Charset?,
    coordinateSystems: Map<CoordinateSystemName, Srid> = mapOf(),
    switchStructuresByType: Map<SwitchType, SwitchStructure>,
    switchTypeNameAliases: Map<String, String>,
    trackNumberIdsByNumber: Map<TrackNumber, IntId<TrackLayoutTrackNumber>>,
): Pair<GeometryPlan, InfraModelFile> {
    val imFile = toInfraModelFile(file, fileName, fileEncodingOverride)
    return parseInfraModelFile(
        source,
        imFile,
        coordinateSystems,
        switchStructuresByType,
        switchTypeNameAliases,
        trackNumberIdsByNumber
    ) to imFile
}

fun parseFromClasspath(
    source: PlanSource,
    fileName: String,
    coordinateSystems: Map<CoordinateSystemName, Srid> = mapOf(),
    switchStructuresByType: Map<SwitchType, SwitchStructure>,
    switchTypeNameAliases: Map<String, String>,
    trackNumberIdsByNumber: Map<TrackNumber, IntId<TrackLayoutTrackNumber>>,
): Pair<GeometryPlan, InfraModelFile> {
    val imFile = toInfraModelFile(FileName(fileName), classpathResourceToString(fileName))
    return parseInfraModelFile(
        source,
        imFile,
        coordinateSystems,
        switchStructuresByType,
        switchTypeNameAliases,
        trackNumberIdsByNumber
    ) to imFile
}

fun toInfraModelFile(file: ByteArray, fileName: FileName, fileEncodingOverride: Charset?) =
    toInfraModelFile(fileName, fileToString(file, fileEncodingOverride))

fun toInfraModelFile(fileName: FileName, fileContent: String) =
    InfraModelFile(name = fileName, content = censorAuthorIdentifyingInfo(fileContent))

fun parseInfraModelFile(
    source: PlanSource,
    file: InfraModelFile,
    coordinateSystems: Map<CoordinateSystemName, Srid> = mapOf(),
    switchStructuresByType: Map<SwitchType, SwitchStructure>,
    switchTypeNameAliases: Map<String, String>,
    trackNumberIdsByNumber: Map<TrackNumber, IntId<TrackLayoutTrackNumber>>,
): GeometryPlan {
    return toGvtPlan(
        source,
        file.name,
        stringToInfraModel(file.content),
        coordinateSystems,
        switchStructuresByType,
        switchTypeNameAliases,
        trackNumberIdsByNumber,
    )
}

fun stringToInfraModel(xmlString: String): InfraModel =
    try {
        unmarshaller.unmarshal(toSaxSource(xmlString)) as InfraModel
    } catch (e: UnmarshalException) {
        logger.warn(xmlString)
        throw InframodelParsingException(
            message = "Failed to unmarshal XML",
            cause = e,
            localizedMessageKey = "$INFRAMODEL_PARSING_KEY_PARENT.xml-invalid",
        )
    }

fun classpathResourceToString(fileName: String): String {
    val resource = InfraModel::class.java.getResource(fileName)
        ?: throw InframodelParsingException("Resource not found: $fileName")
    return xmlBytesToString(resource.readBytes())
}

fun fileToString(file: ByteArray, encodingOverride: Charset?): String {
    return xmlBytesToString(file, encodingOverride)
}

fun fileToString(file: File): String {
    return xmlBytesToString(file.readBytes())
}

fun checkForEmptyFileAndIncorrectFileType(file: ByteArray, contentType: String?, fileName: String, vararg fileContentTypes: MediaType) {
    if (file.isEmpty()) {
        throw InframodelParsingException(
            message = "File \"${fileName}\" is empty",
            localizedMessageKey = "$INFRAMODEL_PARSING_KEY_PARENT.empty",
        )
    }
    if (contentType?.let { !fileContentTypes.contains(MediaType.valueOf(it)) } == true) {
        throw InframodelParsingException(
            message = "File's ${fileName} type is incorrect: ${contentType}",
            localizedMessageKey = "$INFRAMODEL_PARSING_KEY_PARENT.wrong-content-type",
        )
    }
}
