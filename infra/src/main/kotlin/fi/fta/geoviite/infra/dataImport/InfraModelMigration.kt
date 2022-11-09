package fi.fta.geoviite.infra.dataImport

import fi.fta.geoviite.infra.SpringContextUtility
import fi.fta.geoviite.infra.codeDictionary.CodeDictionaryDao
import fi.fta.geoviite.infra.codeDictionary.FeatureType
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.MeasurementMethod.*
import fi.fta.geoviite.infra.dataImport.InfraModelMetadataColumns.*
import fi.fta.geoviite.infra.error.InframodelParsingException
import fi.fta.geoviite.infra.error.InputValidationException
import fi.fta.geoviite.infra.geography.CoordinateSystemDao
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geography.KKJtoETRSTriangulationDao
import fi.fta.geoviite.infra.geography.mapByNameOrAlias
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.PlanSource
import fi.fta.geoviite.infra.geometry.PlanSource.GEOMETRIAPALVELU
import fi.fta.geoviite.infra.geometry.PlanSource.PAIKANNUSPALVELU
import fi.fta.geoviite.infra.geometry.validate
import fi.fta.geoviite.infra.inframodel.parseGeometryPlan
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.resetCollected
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.flywaydb.core.internal.resolver.ChecksumCalculator
import org.flywaydb.core.internal.resource.filesystem.FileSystemResource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.text.Charsets.UTF_8

const val IM_ORIGINAL_FILES_FOLDER = "geometriatietopalvelu"
const val IM_LAYOUT_FILES_FOLDER = "paikannuspalvelu"
private val planZone: ZoneId = ZoneId.of("Europe/Helsinki")

private enum class InfraModelMetadataColumns {
    KPA,
    NAME,
    QUALITY,
    YEAR,
    TIME,
    PROJECT,
    NOTES,
    TRACK_NUMBER,
}

private data class PlanMetaData(
    val kpa: String?,
    val name: String,
    val quality: MeasurementMethod?,
    val date: LocalDate?,
    val notes: String?,
    val trackNumbers: List<Pair<TrackNumber, IntId<TrackLayoutTrackNumber>>>,
)

@Suppress("unused", "ClassName")
class V12_01__InfraModelMigration : BaseJavaMigration() {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val inframodelFilePath: String by lazy { SpringContextUtility.getProperty("geoviite.data.im-path") }
    private val importEnabled: Boolean by lazy { SpringContextUtility.getProperty("geoviite.data.import") }

    private val originalsDir: File by lazy { File(inframodelFilePath).resolve(IM_ORIGINAL_FILES_FOLDER) }
    private val layoutsDir: File by lazy { File(inframodelFilePath).resolve(IM_LAYOUT_FILES_FOLDER) }

    private var imCount = 0
    private var parsingMs = 0L
    private var validationMs = 0L
    private var insertMs = 0L

    override fun migrate(context: Context?) = withUser(ImportUser.IM_IMPORT) {
        if (importEnabled && (originalsDir.isDirectory || layoutsDir.isDirectory)) {
            logger.info("Running InfraModel import: version=$version description=$description path=$inframodelFilePath")
            try {
                val connection = context?.connection
                    ?: throw IllegalStateException("Can't run imports without DB connection")
                val jdbcTemplate = NamedParameterJdbcTemplate(SingleConnectionDataSource(connection, true))

                val csMap = mapByNameOrAlias(CoordinateSystemDao(jdbcTemplate).fetchApplicationCoordinateSystems())
                val switchStructureDao = SwitchStructureDao(jdbcTemplate)
                val kkJtoETRSTriangulationDao = KKJtoETRSTriangulationDao(jdbcTemplate)
                val switchStructures = switchStructureDao.fetchSwitchStructures()
                val featureTypes = CodeDictionaryDao(jdbcTemplate).getFeatureTypes()
                val trackNumberDao = LayoutTrackNumberDao(jdbcTemplate)
                val geometryDao = GeometryDao(jdbcTemplate, kkJtoETRSTriangulationDao)
                val trackNumberIdsByNumber = trackNumberDao.getTrackNumberToIdMapping()
                val switchTypeNameAliases = switchStructureDao.getInframodelAliases()

                importWithSubFolders(
                    geometryDao,
                    originalsDir,
                    GEOMETRIAPALVELU,
                    csMap,
                    switchStructures,
                    switchTypeNameAliases,
                    featureTypes,
                    trackNumberIdsByNumber,
                    UNVERIFIED_DESIGNED_GEOMETRY,
                )
                importWithSubFolders(
                    geometryDao,
                    layoutsDir,
                    PAIKANNUSPALVELU,
                    csMap,
                    switchStructures,
                    switchTypeNameAliases,
                    featureTypes,
                    trackNumberIdsByNumber,
                )

                logger.info(
                    "Inframodel imports done: " +
                            "count=$imCount " +
                            "parsing=${parsingMs}ms validation=${validationMs}ms inserts=${insertMs}ms " +
                            "total=${parsingMs + validationMs + insertMs}ms"
                )
            } catch (e: Exception) {
                logger.error("InfraModel import failed: $e", e)
                throw e
            }
        } else if (importEnabled) {
            logger.warn(
                "InfraModel import enabled, but required folders don't exist: " +
                        "originalsDir=[${originalsDir.absolutePath}] " +
                        "layoutsDir=[${layoutsDir.absolutePath}]"
            )
        } else {
            logger.info("InfraModel import disabled")
        }
    }

    private fun importWithSubFolders(
        geometryDao: GeometryDao,
        baseDir: File,
        type: PlanSource,
        csMap: Map<CoordinateSystemName, Srid>,
        switchStructures: List<SwitchStructure>,
        switchTypeNameAliases: Map<String, String>,
        featureTypes: List<FeatureType>,
        trackNumberIdsByNumber: Map<TrackNumber, IntId<TrackLayoutTrackNumber>>,
        defaultMeasurementMethod: MeasurementMethod? = null,
    ) {
        val metadatas = loadMetadata(baseDir, trackNumberIdsByNumber, defaultMeasurementMethod).associateBy(PlanMetaData::name)
        val switchStructuresByType = switchStructures.associateBy { s -> s.type }
        val switchStructuresById = switchStructures.associateBy { s -> s.id as IntId }

        logger.info("Importing InfraModel files: type=$type path=$baseDir metadatas=${metadatas.size}")
        val files = listXmls(baseDir).map { f -> "" to f } +
                listSubDirs(baseDir).flatMap { subDir -> listXmls(subDir).map { f -> "${subDir.name}/" to f } }
        files.forEach { (dirName, xmlFile) ->
            val readablePath = "$baseDir/$dirName${xmlFile.name}"
            try {
                logger.info(
                    "Parsing inframodel file: path=$readablePath checksum=${calculateChecksum(xmlFile)}"
                )
                val metadata = metadatas[xmlFile.nameWithoutExtension]
                if (metadata == null) logger.warn("No metadata available for file $readablePath")
                val (plan, file) = parseGeometryPlan(
                    xmlFile,
                    "${dirName}${xmlFile.name}",
                    csMap,
                    switchStructuresByType,
                    switchTypeNameAliases,
                    trackNumberIdsByNumber,
                ).let { (plan, file) -> (if (metadata == null) plan else setMetadata(plan, metadata)) to file }
                val validationIssues = validate(plan, featureTypes, switchStructuresById)
                logger.info(
                    "Inserting InfraModel: path=$readablePath validationIssues=${validationIssues.size}"
                )

                geometryDao.insertPlan(plan, file, type)
                imCount++
            }
            catch (e: InframodelParsingException) {
                logger.warn("Failed to parse inframodel: file=$readablePath error=$e")
                throw e
            } catch (e: IllegalArgumentException) {
                logger.warn("Failed to parse inframodel: file=$readablePath error=$e")
                throw e
            } catch (e: InputValidationException) {
                logger.warn("Failed to parse inframodel: file=$readablePath error=$e")
                throw e
            } catch (e: DataAccessException) {
                logger.error("Failed to insert inframodel to DB: file=$readablePath error=$e")
                throw e
            }
        }
        logger.info(
            "Processed InfraModel files: " +
                    "dir=${baseDir.name} count=${files.size} type=$type path=$baseDir timings=${resetCollected()}"
        )
    }

    private fun setMetadata(
        plan: GeometryPlan,
        metadata: PlanMetaData,
    ): GeometryPlan {
        val planYear = plan.planTime?.let { instant -> ZonedDateTime.ofInstant(instant, planZone).year }
        val newPlanTime = if (metadata.date != null && (planYear == null || planYear != metadata.date.year)) {
            val newPlanTime = metadata.date.atStartOfDay(planZone).toInstant()
            if (plan.planTime != null && Duration.between(plan.planTime, newPlanTime).toDays() > 366) {
                logger.warn("Metadata time is after plan content time: meta=${metadata.date} plan=${plan.planTime}")
            }
            newPlanTime
        } else {
            plan.planTime
        }
        val newTrackNumberId =
            if (metadata.trackNumbers.isEmpty()) plan.trackNumberId
            else if (plan.trackNumberId == null) metadata.trackNumbers.firstOrNull()?.second
            else if (metadata.trackNumbers.any { (_, id) -> id == plan.trackNumberId }) plan.trackNumberId
            else {
                logger.warn(
                    "Metadata has different track-numbers than content: " +
                            "plan=${plan.fileName} " +
                            "planTn=${plan.trackNumberDescription} planTnId=${plan.trackNumberId} " +
                            "metadataTns=${metadata.trackNumbers}"
                )
                metadata.trackNumbers.first().second
            }
        return plan.copy(
            planTime = newPlanTime,
            trackNumberId = newTrackNumberId,
            message = joinMessages(plan.message?.toString(), metadata.notes)?.let(::FreeText),
            measurementMethod = metadata.quality ?: plan.measurementMethod,
        )
    }

    private fun joinMessages(original: String?, added: String?) =
        if (original.isNullOrBlank()) added
        else if (added.isNullOrBlank()) original
        else "$original\n$added"

    private fun listSubDirs(baseDir: File): List<File> =
        baseDir.listFiles { d -> d.isDirectory }?.toList() ?: listOf()

    private fun listXmls(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && f.extension == "xml" }?.toList() ?: listOf()

    private fun calculateChecksum(file: File): Int {
        return try {
            ChecksumCalculator.calculate(FileSystemResource(null, file.path, UTF_8, false))
        } catch (e: Exception) {
            // Typically caused by non- UTF-8 encoding, many of which claim to be UTF-8 even though they're not.
            logger.warn("Checksum calculation failed: path=${file.absolutePath} error=$e")
            -1
        }
    }

    private fun toMeasurementMethod(planQualityString: String): MeasurementMethod? = when (planQualityString) {
        "1 - Geodeettinen mittaus" -> UNVERIFIED_DESIGNED_GEOMETRY
        "2 - Kohtuulliset lähtötiedot" -> UNVERIFIED_DESIGNED_GEOMETRY
        "3 - Emma mitatut lähtötiedot" -> TRACK_INSPECTION
        "4 - Epäselvät lähtötiedot" -> null
        "5 - Digitoitu" -> DIGITIZED_AERIAL_IMAGE
        else -> throw IllegalStateException("Unknown quality definition: $planQualityString")
    }

    private fun loadMetadata(
        baseDir: File,
        trackNumberIdsByNumber: Map<TrackNumber, IntId<TrackLayoutTrackNumber>>,
        defaultMeasurementMethod: MeasurementMethod?,
    ): List<PlanMetaData> {
        val metadataFile = CsvFile("${baseDir.absolutePath}/plan_metadata.csv", InfraModelMetadataColumns::class)
        return metadataFile.parseLines { line ->
            PlanMetaData(
                kpa = line.get(KPA),
                name = line.get(NAME),
                quality = line.getNonEmpty(QUALITY)?.let(::toMeasurementMethod) ?: defaultMeasurementMethod,
                date = line.getNonEmpty(TIME)?.let(::parseLocalDate) ?: line.getIntOrNull(YEAR)?.let(::toLocalDate),
                notes = line.getNonEmpty(NOTES) ?: line.getNonEmpty(PROJECT),
                trackNumbers = line.getNonEmpty(TRACK_NUMBER)?.let { tnString ->
                    if (tnString.lowercase().trim() == "ei linkitetty") null
                    else tnString.split(",")
                        .mapNotNull(::tryParseTrackNumber)
                        .mapNotNull { tn -> trackNumberIdsByNumber[tn]?.let { id -> tn to id } }
                } ?: listOf()
            )
        }
    }

    private fun parseLocalDate(dateString: String): LocalDate? =
        if (dateString.length in 4..8 && dateString.all { it.isDigit() }) {
            val year = dateString.substring(0, 4).toInt()
            val month = if (dateString.length >= 6) dateString.substring(4, 6).toInt() else null
            val day = if (dateString.length >= 8) dateString.substring(6, 8).toInt() else null
            LocalDate.of(year, month ?: 1, day ?: 1)
        } else {
            logger.warn("Can't parse metadata date from string $dateString")
            null
        }

    private fun toLocalDate(year: Int): LocalDate = LocalDate.of(year, 1, 1)
}
