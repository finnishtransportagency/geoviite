package fi.fta.geoviite.infra.dataImport

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geography.KKJtoETRSTriangulationDao
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryUnits
import fi.fta.geoviite.infra.switchLibrary.SwitchOwnerDao
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.*
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

@Suppress("unused", "ClassName")
class V11_01__Csv_import_track_numbers : CsvMigration() {

    private val file by lazy { CsvFile("$csvFilePath/01-track-number.csv", TrackNumberColumns::class) }

    override fun migrate(jdbcTemplate: NamedParameterJdbcTemplate) {
        val trackNumberDao = LayoutTrackNumberDao(jdbcTemplate)

        val trackNumbers = measureAndCollect("parse") { createTrackNumbersFromCsv(file) }
        val trackNumberIds = measureAndCollect("insert") {
            trackNumbers.map { trackNumber -> trackNumberDao.insert(trackNumber) }
        }
        logger.info("Imported Track Numbers: count=${trackNumberIds.size} timings=${resetCollected()}")
    }

    override fun getFiles() = listOf(file)
}

@Suppress("unused", "ClassName")
class V14_01__Csv_import_km_posts : CsvMigration() {

    private val file by lazy { CsvFile("$csvFilePath/05-km-post.csv", KmPostColumns::class) }

    override fun migrate(jdbcTemplate: NamedParameterJdbcTemplate) {
        val trackNumberDao = LayoutTrackNumberDao(jdbcTemplate)
        val kmPostDao = LayoutKmPostDao(jdbcTemplate)

        val trackNumbers = trackNumberDao.fetchExternalIdToIdMapping()
        if (trackNumbers.isEmpty()) throw IllegalStateException("No track numbers in DB -> cannot import KM Posts")
        val kmPosts = measureAndCollect("parse") { createKmPostsFromCsv(file, trackNumbers) }
        val kmPostIds = measureAndCollect("insert") { kmPosts.map { kmPost -> kmPostDao.insert(kmPost) } }
        logger.info("Imported KM-posts: count=${kmPostIds.size} timings=${resetCollected()}")
    }

    override fun getFiles() = listOf(file)
}

@Suppress("unused", "ClassName")
class V14_02__Csv_import_switches : CsvMigration() {

    private val switches by lazy { CsvFile("$csvFilePath/06-switch.csv", SwitchColumns::class) }
    private val switchJoints by lazy { CsvFile("$csvFilePath/07-switch-joint.csv", SwitchJointColumns::class) }

    override fun migrate(jdbcTemplate: NamedParameterJdbcTemplate) {
        val switchDao = LayoutSwitchDao(jdbcTemplate)
        val switchStructureDao = SwitchStructureDao(jdbcTemplate)
        val switchOwnerDao = SwitchOwnerDao(jdbcTemplate)

        val switchStructuresByType = switchStructureDao.fetchSwitchStructures().associateBy { it.type }
        val switchOwners = switchOwnerDao.fetchSwitchOwners()

        val switches = measureAndCollect("parsing") {
            createSwitchesFromCsv(switches, switchJoints, switchStructuresByType, switchOwners)
        }
        val switchIds = measureAndCollect("insert") {
            switches.map { switch -> switchDao.insert(switch).id }
        }
        logger.info("Imported Switches: count=${switchIds.size} timings=${resetCollected()}")
    }

    override fun getFiles() = listOf(switches, switchJoints)
}

@Suppress("unused", "ClassName")
class V14_03__Csv_import_reference_lines : CsvMigration() {
    private val referenceLines by lazy {
        CsvFile("$csvFilePath/02-reference-line.csv", ReferenceLineColumns::class)
    }

    private val referenceLineMetadata by lazy {
        CsvFile("$csvFilePath/10-reference-line-meta.csv", AlignmentMetaColumns::class)
    }

    override fun migrate(jdbcTemplate: NamedParameterJdbcTemplate) {
        val metadataDao = InitAlignmentMetadataDao(jdbcTemplate)
        val trackNumberDao = LayoutTrackNumberDao(jdbcTemplate)
        val trackNumbers = trackNumberDao.fetchExternalIdToIdMapping()
        val referenceLineDao = ReferenceLineDao(jdbcTemplate)
        val alignmentDao = LayoutAlignmentDao(jdbcTemplate)
        val kkJtoETRSTriangulationDao = KKJtoETRSTriangulationDao(jdbcTemplate)
        if (trackNumbers.isEmpty()) {
            throw IllegalStateException("No track numbers in DB -> cannot import reference lines")
        }
        val metadataList = getCsvMetaData<ReferenceLine>(jdbcTemplate, referenceLineMetadata)
        val metadataIds = metadataDao.insertMetadata(metadataList)
        val metadata = metadataList
            .mapIndexed { index, md -> md.copy(id = metadataIds[index]) }
            .groupBy { am -> am.alignmentOid }

        val kkjToEtrsTriangulationNetwork = kkJtoETRSTriangulationDao.fetchTriangulationNetwork()

        referenceLines.use { file ->
            var count = 0
            measureAndCollect("total") {
                createReferenceLinesFromCsv(
                    file,
                    metadata,
                    trackNumbers,
                    kkjToEtrsTriangulationNetwork
                ).forEach { csvReferenceLine ->
                    measureAndCollect("insert") {
                        val alignmentVersion = alignmentDao.insert(csvReferenceLine.alignment)
                        metadataDao.linkMetadata(alignmentVersion.id, csvReferenceLine.segmentMetadataIds)
                        referenceLineDao.insert(
                            csvReferenceLine.referenceLine.copy(alignmentVersion = alignmentVersion)
                        )
                    }
                    count++
                }
            }
            logger.info("Imported ReferenceLines: count=$count timings=${resetCollected()}")
        }
    }

    override fun getFiles() = listOf(referenceLines, referenceLineMetadata)
}

@Suppress("unused", "ClassName")
class V14_04__Csv_import_location_tracks : CsvMigration() {

    private val alignments by lazy {
        CsvFile("$csvFilePath/03-location-track.csv", LocationTrackColumns::class)
    }
    private val alignmentCsvMetadata by lazy {
        CsvFile("$csvFilePath/04-location-track-meta.csv", AlignmentMetaColumns::class)
    }
    private val alignmentSwitchLinks by lazy {
        CsvFile("$csvFilePath/08-location-track-switch-link.csv", AlignmentSwitchLinkColumns::class)
    }

    override fun migrate(jdbcTemplate: NamedParameterJdbcTemplate) {
        val metadataDao = InitAlignmentMetadataDao(jdbcTemplate)
        val trackNumberDao = LayoutTrackNumberDao(jdbcTemplate)
        val locationTrackDao = LocationTrackDao(jdbcTemplate)
        val alignmentDao = LayoutAlignmentDao(jdbcTemplate)
        val switchDao = LayoutSwitchDao(jdbcTemplate)
        val kkJtoETRSTriangulationDao = KKJtoETRSTriangulationDao(jdbcTemplate)
        val switchStructureDao = SwitchStructureDao(jdbcTemplate)
        val trackNumbers = trackNumberDao.fetchExternalIdToIdMapping()

        if (trackNumbers.isEmpty()) {
            throw IllegalStateException("No track numbers in DB -> cannot import location tracks")
        }
        val metadataList = getCsvMetaData<LocationTrack>(jdbcTemplate, alignmentCsvMetadata)
        val metadataIds = metadataDao.insertMetadata(metadataList)
        val metadata = metadataList
            .mapIndexed { index, md -> md.copy(id = metadataIds[index]) }
            .groupBy { am -> am.alignmentOid }

        val switchLinks = getSwitchLinks(
            switchDao.getLinkingInfoOfExistingSwitches(),
            switchStructureDao.fetchSwitchStructures().associateBy { ss -> ss.id as IntId },
        )
        val kkjToEtrsTriangulationNetwork = kkJtoETRSTriangulationDao.fetchTriangulationNetwork()

        alignments.use { alignments ->
            var count = 0
            val insertedTrackIds = mutableMapOf<Oid<LocationTrack>, IntId<LocationTrack>>()
            measureAndCollect("total") {
                val tracks = createLocationTracksFromCsv(
                    alignments,
                    metadata,
                    switchLinks,
                    trackNumbers,
                    kkjToEtrsTriangulationNetwork
                )
                tracks.forEach { csvLocationTrack -> measureAndCollect("insert") {
                    val alignmentVersion = alignmentDao.insert(csvLocationTrack.layoutAlignment)
                    metadataDao.linkMetadata(alignmentVersion.id, csvLocationTrack.segmentMetadataIds)
                    val duplicateOfId = csvLocationTrack.duplicateOfExternalId?.let { oid -> insertedTrackIds[oid] }
                    val rowVersion = locationTrackDao.insert(
                        csvLocationTrack.locationTrack.copy(
                            alignmentVersion = alignmentVersion,
                            duplicateOf = duplicateOfId,
                        )
                    )
                    val extId = csvLocationTrack.locationTrack.externalId
                        ?: throw IllegalStateException("Imported LocationTracks must have an external id")
                    insertedTrackIds[extId] = rowVersion.id
                    count++
                } }
                logger.info("Imported LocationTracks: count=$count timings=${resetCollected()}")
            }
        }
    }

    override fun getFiles() = listOf(alignments, alignmentCsvMetadata, alignmentSwitchLinks)

    private fun getSwitchLinks(
        linkingInfo: Map<Oid<TrackLayoutSwitch>, SwitchLinkingInfo>,
        switchStructures: Map<IntId<SwitchStructure>, SwitchStructure>,
    ): Map<Oid<LocationTrack>, List<AlignmentSwitchLink>> {
        return createAlignmentSwitchLinks(
            alignmentSwitchLinks,
            linkingInfo,
            switchStructures
        ).groupBy { sl -> sl.alignmentOid }
    }
}

private inline fun <reified T> getCsvMetaData(
    jdbcTemplate: NamedParameterJdbcTemplate,
    file: CsvFile<AlignmentMetaColumns>,
): List<AlignmentCsvMetaData<T>> {
    val geometryDao = GeometryDao(
        jdbcTemplate,
        KKJtoETRSTriangulationDao(jdbcTemplate)
    )
    val geometryProvider = { fileName: FileName, alignmentName: AlignmentName ->
        val match = fetchAlignmentGeometry(jdbcTemplate, geometryDao, fileName, alignmentName)
            ?: normalizeAlignmentName(alignmentName)?.let { normalized ->
                fetchAlignmentGeometry(jdbcTemplate, geometryDao, fileName, normalized)
            }
        if (match == null) {
            logger.warn("Didn't find geometry alignment for ${T::class.simpleName}: file=$fileName alignment=$alignmentName")
        }
        match
    }
    return createAlignmentMetadataFromCsv(file, geometryProvider)
}

private fun normalizeAlignmentName(name: AlignmentName): AlignmentName? =
    if (name.length < 3 && name.all(Char::isDigit)) name.padStart(3, '0')
    else null

private fun fetchAlignmentGeometry(
    jdbcTemplate: NamedParameterJdbcTemplate,
    geometryDao: GeometryDao,
    fileName: FileName,
    alignmentName: AlignmentName,
): AlignmentImportGeometry? {
    val sql = """
        select 
          alignment.id,
          plan.srid,
          plan.vertical_coordinate_system, 
          plan.direction_unit, 
          plan.linear_unit
        from geometry.alignment
          inner join geometry.plan on alignment.plan_id = plan.id
          inner join geometry.plan_file on plan.id = plan_file.plan_id
        where plan_file.name like :file_name_matcher 
          and alignment.name = :alignment_name
          and plan.source = 'PAIKANNUSPALVELU'
    """.trimIndent()
    val params = mapOf(
        "file_name_matcher" to "${fileName}.xml",
        "alignment_name" to alignmentName,
    )
    val alignments = jdbcTemplate.query(sql, params) { rs, _ ->
        val alignmentId: IntId<GeometryAlignment> = rs.getIntId("id")
        val units = GeometryUnits(
            coordinateSystemSrid = rs.getSridOrNull("srid"),
            coordinateSystemName = null,
            verticalCoordinateSystem = rs.getEnumOrNull<VerticalCoordinateSystem>("vertical_coordinate_system"),
            directionUnit = rs.getEnum("direction_unit"),
            linearUnit = rs.getEnum("linear_unit"),
        )
        AlignmentImportGeometry(
            id = alignmentId,
            coordinateSystemSrid = units.coordinateSystemSrid,
            elements = geometryDao.fetchElements(alignmentId, units),
        )
    }
    return if (alignments.size == 1) {
        logger.debug("Found metadata alignment: file=$fileName alignment=$alignmentName")
        alignments.first()
    } else if (alignments.size > 1) {
        logger.warn(
            "Found multiple geometry alignments for one metadata (picking first): " +
                    "file=$fileName alignment=$alignmentName count=${alignments.size}"
        )
        alignments.first()
    } else {
        null
    }
}
