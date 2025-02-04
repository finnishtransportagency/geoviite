package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.error.InframodelParsingException
import fi.fta.geoviite.infra.geometry.PlanSource
import fi.fta.geoviite.infra.logging.Loggable
import fi.fta.geoviite.infra.util.FileName
import org.apache.commons.codec.digest.DigestUtils

typealias FileHash = String

data class InfraModelFile(val name: FileName, val content: String) : Loggable {
    val hash: FileHash by lazy { DigestUtils.md5Hex(content) }

    init {
        require(!containsIdentifyingInfo(content)) { "Identifying info must be censored from IM before storing" }
        if (content.isBlank())
            throw InframodelParsingException(
                message = "File \"${name}\" is empty",
                localizedMessageKey = "$INFRAMODEL_PARSING_KEY_PARENT.empty",
            )
    }

    override fun toLog(): String = logFormat("name" to name, "hash" to hash)
}

data class InfraModelFileWithSource(val file: InfraModelFile, val source: PlanSource)

private val authorTagRegex = Regex("<Author [^>]*>")
private val createdByRegex = Regex("createdBy=\"[^\"]+\"")
private val createdByEmailRegex = Regex("createdByEmail=\"[^\"]+\"")
const val EMPTY_CREATED_BY = "createdBy=\"\""
const val EMPTY_CREATED_BY_EMAIL = "createdByEmail=\"\""

fun containsIdentifyingInfo(content: String): Boolean =
    getAuthorTag(content)?.let { tag -> tag.contains(createdByRegex) || tag.contains(createdByEmailRegex) } ?: false

fun censorAuthorIdentifyingInfo(content: String): String {
    val originalAuthorTag = getAuthorTag(content)
    return if (originalAuthorTag == null) {
        content
    } else {
        val censoredTag: String =
            originalAuthorTag
                .replace(createdByRegex, EMPTY_CREATED_BY)
                .replace(createdByEmailRegex, EMPTY_CREATED_BY_EMAIL)
        content.replace(originalAuthorTag, censoredTag)
    }
}

fun getAuthorTag(content: String): String? =
    authorTagRegex.findAll(content).toList().let { matches ->
        if (matches.size > 1) throw InframodelParsingException("Multiple Author tags in one InfraModel")
        matches.firstOrNull()?.value
    }
