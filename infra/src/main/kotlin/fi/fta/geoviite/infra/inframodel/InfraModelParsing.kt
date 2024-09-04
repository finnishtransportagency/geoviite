package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.error.InframodelParsingException
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geometry.GeometryIssueType
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryValidationIssue
import fi.fta.geoviite.infra.geometry.PlanSource
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchType
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.xmlBytesToString
import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.Marshaller
import jakarta.xml.bind.UnmarshalException
import jakarta.xml.bind.Unmarshaller
import java.io.File
import java.io.StringReader
import java.nio.charset.Charset
import javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.sax.SAXSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile
import org.xml.sax.InputSource

// Schema from https://buildingsmart.fi/infra/schema/im_current.html
private const val SCHEMA_LOCATION = "/xml/inframodel.xsd"

const val INFRAMODEL_PARSING_KEY_PARENT = "error.infra-model.parsing"
const val INFRAMODEL_PARSING_KEY_GENERIC = "$INFRAMODEL_PARSING_KEY_PARENT.generic"
const val INFRAMODEL_PARSING_KEY_EMPTY = "$INFRAMODEL_PARSING_KEY_PARENT.empty"

data class ParsingError(override val localizationKey: LocalizationKey) : GeometryValidationIssue {
    override val issueType = GeometryIssueType.PARSING_ERROR
}

private val jaxbContext: JAXBContext by lazy {
    JAXBContext.newInstance(InfraModel403::class.java, InfraModel404::class.java)
}

private val schema: Schema by lazy {
    val language = W3C_XML_SCHEMA_NS_URI
    val factory = SchemaFactory.newInstance(language)
    factory.newSchema(
        InfraModel403::class.java.getResource(SCHEMA_LOCATION)
            ?: throw IllegalArgumentException("Failed to load schema from classpath:$SCHEMA_LOCATION")
    )
}

// Unlike jaxbContexts, Marshallers are not thread-safe
val unmarshaller: Unmarshaller
    get() = jaxbContext.createUnmarshaller()
val marshaller: Marshaller
    get() = jaxbContext.createMarshaller()

private val saxParserFactory: SAXParserFactory by lazy {
    val spf = SAXParserFactory.newInstance()

    // Validate
    spf.isNamespaceAware = true
    // This id only DTD validation, as per http://www.w3.org/TR/REC-xml#proc-types - we don't want
    // that
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

fun toSaxSource(xmlString: String) =
    SAXSource(saxParserFactory.newSAXParser().xmlReader, InputSource(StringReader(xmlString)))

fun classPathToInfraModelFile(fileName: String) =
    toInfraModelFile(FileName(fileName), classpathResourceToString(fileName))

fun toInfraModelFile(file: MultipartFile, fileEncodingOverride: Charset?): InfraModelFile {
    val name = FileName(file)
    assertContentType(name, file.contentType, MediaType.APPLICATION_XML, MediaType.TEXT_XML)
    return toInfraModelFile(file.bytes, name, fileEncodingOverride)
}

fun toInfraModelFile(file: ByteArray, fileName: FileName, fileEncodingOverride: Charset?): InfraModelFile {
    toInfraModelFile(fileName, fileToString(file, fileEncodingOverride))
    return toInfraModelFile(fileName, fileToString(file, fileEncodingOverride))
}

fun toInfraModelFile(fileName: FileName, fileContent: String) =
    InfraModelFile(name = fileName, content = censorAuthorIdentifyingInfo(fileContent))

fun parseInfraModelFile(
    source: PlanSource,
    file: InfraModelFile,
    coordinateSystems: Map<CoordinateSystemName, Srid> = mapOf(),
    switchStructuresByType: Map<SwitchType, SwitchStructure>,
    switchTypeNameAliases: Map<String, String>,
): GeometryPlan {
    return toGvtPlan(
        source,
        file.name,
        toInfraModel(file),
        coordinateSystems,
        switchStructuresByType,
        switchTypeNameAliases,
    )
}

fun toInfraModel(file: InfraModelFile): InfraModel {
    return try {
        unmarshaller.unmarshal(toSaxSource(file.content)) as InfraModel
    } catch (e: UnmarshalException) {
        logger.warn("InfraModel parsing failed: fileName=${file.name}", e)
        throw InframodelParsingException(
            message = "Failed to unmarshal XML",
            cause = e,
            localizedMessageKey = "$INFRAMODEL_PARSING_KEY_PARENT.xml-invalid",
        )
    }
}

fun classpathResourceToString(fileName: String): String {
    val resource =
        InfraModel::class.java.getResource(fileName)
            ?: throw InframodelParsingException("Resource not found: $fileName")
    return xmlBytesToString(resource.readBytes())
}

fun fileToString(file: ByteArray, encodingOverride: Charset?): String {
    return xmlBytesToString(file, encodingOverride)
}

fun fileToString(file: File): String {
    return xmlBytesToString(file.readBytes())
}

fun assertContentType(fileName: FileName, contentType: String?, vararg fileContentTypes: MediaType) {
    val isCorrectType = contentType?.let { fileContentTypes.contains(MediaType.valueOf(it)) }
    if (isCorrectType == false)
        throw InframodelParsingException(
            message = "File's $fileName type is incorrect: $contentType",
            localizedMessageKey = "$INFRAMODEL_PARSING_KEY_PARENT.wrong-content-type",
        )
}
