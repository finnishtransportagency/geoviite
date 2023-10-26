package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.util.CsvEntry
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.printCsv
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.stream.Collectors

@Service
class LayoutTrackNumberService(
    dao: LayoutTrackNumberDao,
    private val referenceLineService: ReferenceLineService,
    private val geocodingService: GeocodingService,
    private val alignmentService: LayoutAlignmentService,
) : DraftableObjectService<TrackLayoutTrackNumber, LayoutTrackNumberDao>(dao) {

    @Transactional
    fun insert(saveRequest: TrackNumberSaveRequest): IntId<TrackLayoutTrackNumber> {
        logger.serviceCall("insert", "trackNumber" to saveRequest.number)
        val draftSaveResponse = saveDraftInternal(
            TrackLayoutTrackNumber(
                number = saveRequest.number,
                description = saveRequest.description,
                state = saveRequest.state,
                externalId = null,
            )
        )
        referenceLineService.addTrackNumberReferenceLine(draftSaveResponse.id, saveRequest.startAddress)
        return draftSaveResponse.id
    }

    @Transactional
    fun update(
        id: IntId<TrackLayoutTrackNumber>,
        saveRequest: TrackNumberSaveRequest,
    ): IntId<TrackLayoutTrackNumber> {
        logger.serviceCall("update", "trackNumber" to saveRequest.number)
        val original = getInternalOrThrow(DRAFT, id)
        val draftSaveResponse = saveDraftInternal(
            original.copy(
                number = saveRequest.number,
                description = saveRequest.description,
                state = saveRequest.state,
            )
        )
        referenceLineService.updateTrackNumberReferenceLine(id, saveRequest.startAddress)
        return draftSaveResponse.id
    }


    @Transactional
    fun updateExternalId(
        id: IntId<TrackLayoutTrackNumber>,
        oid: Oid<TrackLayoutTrackNumber>,
    ): DaoResponse<TrackLayoutTrackNumber> {
        logger.serviceCall("updateExternalIdForTrackNumber", "id" to id, "oid" to oid)

        val original = getInternalOrThrow(DRAFT, id)
        val trackLayoutTrackNumber = original.copy(externalId = oid)

        return saveDraftInternal(trackLayoutTrackNumber)
    }

    @Transactional
    fun deleteDraftOnlyTrackNumberAndReferenceLine(id: IntId<TrackLayoutTrackNumber>): IntId<TrackLayoutTrackNumber> {
        val trackNumber = getInternalOrThrow(DRAFT, id)
        val referenceLine = referenceLineService.getByTrackNumber(DRAFT, id)
            ?: throw IllegalStateException("Found Track Number without Reference Line $id")

        referenceLineService.deleteDraftOnlyReferenceLine(referenceLine)
        return deleteDraftOnlyTrackNumber(trackNumber).id
    }

    private fun deleteDraftOnlyTrackNumber(trackNumber: TrackLayoutTrackNumber): DaoResponse<TrackLayoutTrackNumber> {
        if (trackNumber.getDraftType() != DraftType.NEW_DRAFT) throw DeletingFailureException("Trying to delete non-draft Track Number")
        require(trackNumber.id is IntId) { "Trying to delete or reset track number not yet saved to database" }
        return trackNumber.draft?.draftRowId.let { draftRowId ->
            require(draftRowId is IntId) { "Trying to delete draft Track Number that isn't yet stored in database" }
            dao.deleteDraft(draftRowId)
        }
    }

    fun list(publishType: PublishType, searchTerm: FreeText, limit: Int?): List<TrackLayoutTrackNumber> {
        logger.serviceCall(
            "list", "publishType" to publishType, "searchTerm" to searchTerm, "limit" to limit
        )
        return searchTerm.toString().trim().takeIf(String::isNotEmpty)?.let { term ->
            listInternal(publishType, true).filter { trackLayoutTrackNumber ->
                idMatches(term, trackLayoutTrackNumber) || contentMatches(
                    term, trackLayoutTrackNumber
                )
            }.sortedBy(TrackLayoutTrackNumber::number).let { list -> if (limit != null) list.take(limit) else list }
        } ?: listOf()
    }

    private fun idMatches(term: String, trackLayoutTrackNumber: TrackLayoutTrackNumber) =
        trackLayoutTrackNumber.externalId.toString() == term || trackLayoutTrackNumber.id.toString() == term

    private fun contentMatches(term: String, trackLayoutTrackNumber: TrackLayoutTrackNumber) =
        trackLayoutTrackNumber.exists && trackLayoutTrackNumber.number.toString().replace("  ", " ").contains(term, true)

    fun mapById(publishType: PublishType) = list(publishType).associateBy { tn -> tn.id as IntId }

    fun mapByNumber(publishType: PublishType) = list(publishType).associateBy(TrackLayoutTrackNumber::number)

    override fun createDraft(item: TrackLayoutTrackNumber) = draft(item)

    override fun createPublished(item: TrackLayoutTrackNumber) = published(item)

    fun find(trackNumber: TrackNumber, publishType: PublishType): List<TrackLayoutTrackNumber> {
        logger.serviceCall("find", "trackNumber" to trackNumber, "publishType" to publishType)
        return dao.fetchVersions(publishType, false, trackNumber).map(dao::fetch)
    }

    fun getKmLengths(
        publishType: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): List<TrackLayoutKmLengthDetails>? {
        logger.serviceCall(
            "getKmLengths",
            "trackNumberId" to trackNumberId,
            "publishType" to publishType,
        )

        return geocodingService.getGeocodingContext(publishType, trackNumberId)?.let { context ->
            val distances = getKmPostDistances(context)
            val referenceLineLength = context.referenceLineGeometry.length
            val trackNumber = context.trackNumber
            val startPoint = context.referenceLineAddresses.startPoint

            //First km post is usually on another reference line, and therefore it has to be generated here
            listOf(
                TrackLayoutKmLengthDetails(
                    trackNumber = trackNumber.number,
                    kmNumber = startPoint.address.kmNumber,
                    startM = roundTo3Decimals(context.startAddress.meters.negate()),
                    endM = roundTo3Decimals(distances.firstOrNull()?.second ?: referenceLineLength),
                    locationSource = GeometrySource.GENERATED,
                    location = startPoint.point.toPoint()
                )
            ) + distances.mapIndexed { index, (kmPost, startM) ->
                val endM = distances.getOrNull(index + 1)?.second ?: referenceLineLength

                TrackLayoutKmLengthDetails(
                    trackNumber = trackNumber.number,
                    kmNumber = kmPost.kmNumber,
                    startM = roundTo3Decimals(startM),
                    endM = roundTo3Decimals(endM),
                    location = kmPost.location,
                    locationSource = if (kmPost.sourceId != null) GeometrySource.PLAN else GeometrySource.IMPORTED
                )
            }
        }
    }

    fun getKmLengthsAsCsv(
        publishType: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        startKmNumber: KmNumber? = null,
        endKmNumber: KmNumber? = null,
    ): String {
        logger.serviceCall(
            "getKmLengthsAsCsv",
            "trackNumberId" to trackNumberId,
            "publishType" to publishType,
            "startKmNumber" to startKmNumber,
            "endKmNumber" to endKmNumber,
        )

        val kmLengths = getKmLengths(publishType, trackNumberId) ?: emptyList()

        val filteredKmLengths = kmLengths.filter { kmPost ->
            val start = startKmNumber ?: kmLengths.first().kmNumber
            val end = endKmNumber ?: kmLengths.last().kmNumber

            kmPost.kmNumber in start..end
        }

        return asCsvFile(filteredKmLengths)
    }

    fun getAllKmLengthsAsCsv(
        publishType: PublishType,
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
    ): String {
        val kmLengths = trackNumberIds
            .parallelStream()
            .flatMap { trackNumberId ->
                (getKmLengths(publishType, trackNumberId) ?: emptyList()).stream()
            }
            .sorted(compareBy { kmLengthDetails -> kmLengthDetails.trackNumber })
            .collect(Collectors.toList())

        return asCsvFile(kmLengths)
    }

    private fun getKmPostDistances(context: GeocodingContext) = context.kmPosts.map { kmPost ->
        val distance = kmPost.location?.let { loc ->
            context.getM(loc)?.first
        }
        checkNotNull(distance) { "Couldn't calculated distance for km post, id=${kmPost.id} location=${kmPost.location}" }

        kmPost to distance
    }

    @Transactional(readOnly = true)
    fun getMetadataSections(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        publishType: PublishType,
        boundingBox: BoundingBox?,
    ): List<AlignmentPlanSection> {
        logger.serviceCall(
            "getSectionsByPlan",
            "trackNumberId" to trackNumberId,
            "publishType" to publishType,
            "boundingBox" to boundingBox
        )
        val trackNumber = getOrThrow(publishType, trackNumberId)
        val referenceLine = referenceLineService.getByTrackNumber(publishType, trackNumberId)
            ?: throw NoSuchEntityException("No ReferenceLine for TrackNumber", trackNumberId)
        val geocodingContext = geocodingService.getGeocodingContext(publishType, trackNumberId)
        return if (geocodingContext != null && referenceLine.alignmentVersion != null) {
            alignmentService.getGeometryMetadataSections(
                referenceLine.alignmentVersion,
                trackNumber.externalId,
                boundingBox,
                geocodingContext,
            )
        } else listOf()
    }

    fun officialDuplicateNameExistsFor(trackNumberId: IntId<TrackLayoutTrackNumber>): Boolean {
        logger.serviceCall("officialDuplicateNameExistsFor", "trackNumberId" to trackNumberId)
        return dao.officialDuplicateNumberExistsFor(trackNumberId)
    }
}

private fun asCsvFile(items: List<TrackLayoutKmLengthDetails>): String {
    val columns = mapOf<TrackLayoutKmPostTableColumn, (item: TrackLayoutKmLengthDetails) -> Any?>(
        TrackLayoutKmPostTableColumn.TRACK_NUMBER to { it.trackNumber },
        TrackLayoutKmPostTableColumn.KILOMETER to { it.kmNumber },
        TrackLayoutKmPostTableColumn.START_M to { it.startM },
        TrackLayoutKmPostTableColumn.END_M to { it.endM },
        TrackLayoutKmPostTableColumn.LENGTH to { it.length },
        TrackLayoutKmPostTableColumn.LOCATION_E to { it.location?.x?.let(::roundTo3Decimals) },
        TrackLayoutKmPostTableColumn.LOCATION_N to { it.location?.y?.let(::roundTo3Decimals) },
        TrackLayoutKmPostTableColumn.WARNING to { kmPost ->
            if (kmPost.location != null && kmPost.locationSource == GeometrySource.IMPORTED) getTranslation("projected-location-warning")
            else if (kmPost.location != null && kmPost.locationSource == GeometrySource.GENERATED) getTranslation("start-address-location-warning")
            else ""
        }).map { (column, fn) ->
        CsvEntry(getTranslation("$column-header"), fn)
    }

    return printCsv(columns, items)
}
