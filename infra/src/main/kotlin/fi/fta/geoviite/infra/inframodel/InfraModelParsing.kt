package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.InframodelParsingException
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geometry.ErrorType
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.ValidationError
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchType
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.LocalizationKey
import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.Marshaller
import jakarta.xml.bind.UnmarshalException
import jakarta.xml.bind.Unmarshaller
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.File
import java.io.StringReader
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI
import javax.xml.parsers.SAXParserFactory
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamReader
import javax.xml.transform.sax.SAXSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory

// Schema from https://buildingsmart.fi/infra/schema/im_current.html
private const val SCHEMA_LOCATION = "/xml/inframodel.xsd"

const val INFRAMODEL_PARSING_KEY_PARENT = "error.infra-model.parsing"
const val INFRAMODEL_PARSING_KEY_GENERIC = "$INFRAMODEL_PARSING_KEY_PARENT.generic"

data class ParsingError(private val key: String) : ValidationError {
    override val errorType = ErrorType.PARSING_ERROR
    override val localizationKey = LocalizationKey(key)
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

private const val UTF8_BOM: String = "\uFEFF"

fun parseGeometryPlan(
    file: File,
    fileName: String = file.name,
    coordinateSystems: Map<CoordinateSystemName, Srid> = mapOf(),
    switchStructuresByType: Map<SwitchType, SwitchStructure>,
    switchTypeNameAliases: Map<String, String>,
    trackNumberIdsByNumber: Map<TrackNumber, IntId<TrackLayoutTrackNumber>>,
): Pair<GeometryPlan, InfraModelFile> {
    val imFile = toInfraModelFile(fileName, fileToString(file))

    return parseFromString(
        imFile,
        coordinateSystems,
        switchStructuresByType,
        switchTypeNameAliases,
        trackNumberIdsByNumber
    ) to imFile
}

fun parseGeometryPlan(
    file: MultipartFile,
    fileEncodingOverride: Charset?,
    coordinateSystems: Map<CoordinateSystemName, Srid> = mapOf(),
    switchStructuresByType: Map<SwitchType, SwitchStructure>,
    switchTypeNameAliases: Map<String, String>,
    trackNumberIdsByNumber: Map<TrackNumber, IntId<TrackLayoutTrackNumber>>,
): Pair<GeometryPlan, InfraModelFile> {
    val imFile = toInfraModelFile(file.originalFilename ?: file.name, fileToString(file, fileEncodingOverride))
    return parseFromString(
        imFile,
        coordinateSystems,
        switchStructuresByType,
        switchTypeNameAliases,
        trackNumberIdsByNumber
    ) to imFile
}

fun parseFromClasspath(
    fileName: String,
    coordinateSystems: Map<CoordinateSystemName, Srid> = mapOf(),
    switchStructuresByType: Map<SwitchType, SwitchStructure>,
    switchTypeNameAliases: Map<String, String>,
    trackNumberIdsByNumber: Map<TrackNumber, IntId<TrackLayoutTrackNumber>>,
): Pair<GeometryPlan, InfraModelFile> {
    val imFile = toInfraModelFile(fileName, classpathResourceToString(fileName))
    return parseFromString(
        imFile,
        coordinateSystems,
        switchStructuresByType,
        switchTypeNameAliases,
        trackNumberIdsByNumber
    ) to imFile
}

fun toInfraModelFile(fileName: String, fileContent: String) =
    InfraModelFile(name = FileName(fileName), content = censorAuthorIdentifyingInfo(fileContent))

fun parseFromString(
    file: InfraModelFile,
    coordinateSystems: Map<CoordinateSystemName, Srid> = mapOf(),
    switchStructuresByType: Map<SwitchType, SwitchStructure>,
    switchTypeNameAliases: Map<String, String>,
    trackNumberIdsByNumber: Map<TrackNumber, IntId<TrackLayoutTrackNumber>>,
): GeometryPlan {
    return toGvtPlan(
        file.name,
        stringToInfraModel(file.content),
        coordinateSystems,
        switchStructuresByType,
        switchTypeNameAliases,
        trackNumberIdsByNumber,
    )
}

val xmlCharsets = listOf(
    StandardCharsets.UTF_8,
    StandardCharsets.UTF_16,
    StandardCharsets.UTF_16BE,
    StandardCharsets.UTF_16LE,
    StandardCharsets.US_ASCII,
    StandardCharsets.ISO_8859_1,
)

fun findXmlCharset(name: String) = xmlCharsets.find { cs -> cs.name() == name }

fun getEncoding(bytes: ByteArray): Charset {
    ByteArrayInputStream(bytes).use { stream ->
        val xmlStreamReader: XMLStreamReader = XMLInputFactory.newInstance().createXMLStreamReader(stream)
        val fileEncoding = xmlStreamReader.encoding
        val encodingFromXMLDeclaration = xmlStreamReader.characterEncodingScheme
        return (encodingFromXMLDeclaration ?: fileEncoding)?.let(::findXmlCharset) ?: StandardCharsets.UTF_8
    }
}

fun stringToInfraModel(xmlString: String): InfraModel =
    try {
        unmarshaller.unmarshal(toSaxSource(xmlString)) as InfraModel
    } catch (e: UnmarshalException) {
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

fun fileToString(file: MultipartFile, encodingOverride: Charset?): String {
    return xmlBytesToString(file.bytes, encodingOverride)
}

fun fileToString(file: File): String {
    return xmlBytesToString(file.readBytes())
}

fun xmlBytesToString(bytes: ByteArray, encodingOverride: Charset? = null): String {
    return checkUTF8BOM(String(bytes, encodingOverride ?: getEncoding(bytes)))
}

fun checkUTF8BOM(content: String): String {
    return if (content.startsWith(UTF8_BOM)) content.substring(1) else content
}

fun checkForEmptyFileAndIncorrectFileType(file: MultipartFile, vararg fileContentTypes: MediaType) {
    if (file.isEmpty) {
        throw InframodelParsingException(
            message = "File \"${file.name}\" is empty",
            localizedMessageKey = "$INFRAMODEL_PARSING_KEY_PARENT.empty",
        )
    }
    if (file.contentType?.let { !fileContentTypes.contains(MediaType.valueOf(it)) } == true) {
        throw InframodelParsingException(
            message = "File's ${file.name} type is incorrect: ${file.contentType}",
            localizedMessageKey = "$INFRAMODEL_PARSING_KEY_PARENT.wrong-content-type",
        )
    }
}
