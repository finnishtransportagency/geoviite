package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingContextCreateResult
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.util.CsvEntry
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
) : LayoutAssetService<TrackLayoutTrackNumber, LayoutTrackNumberDao>(dao) {

    @Transactional
    fun insert(saveRequest: TrackNumberSaveRequest): IntId<TrackLayoutTrackNumber> {
        logger.serviceCall("insert", "trackNumber" to saveRequest)
        val draftSaveResponse = saveDraftInternal(
            TrackLayoutTrackNumber(
                number = saveRequest.number,
                description = saveRequest.description,
                state = saveRequest.state,
                externalId = null,
                contextData = LayoutContextData.newDraft(),
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
        logger.serviceCall("update", "trackNumber" to saveRequest)
        val original = dao.getOrThrow(DRAFT, id)
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

        val original = dao.getOrThrow(DRAFT, id)
        val trackLayoutTrackNumber = original.copy(externalId = oid)

        return saveDraftInternal(trackLayoutTrackNumber)
    }

    @Transactional
    fun deleteDraftAndReferenceLine(id: IntId<TrackLayoutTrackNumber>): IntId<TrackLayoutTrackNumber> {
        logger.serviceCall("deleteDraftAndReferenceLine", "id" to id)
        referenceLineService.deleteDraftByTrackNumberId(id)
        return deleteDraft(id).id
    }

    override fun sortSearchResult(list: List<TrackLayoutTrackNumber>) = list.sortedBy(TrackLayoutTrackNumber::number)

    override fun idMatches(term: String, item: TrackLayoutTrackNumber) =
        item.externalId.toString() == term || item.id.toString() == term

    override fun contentMatches(term: String, item: TrackLayoutTrackNumber) =
        item.exists && item.number.toString().replace("  ", " ").contains(term, true)

    fun mapById(publishType: PublishType) = list(publishType).associateBy { tn -> tn.id as IntId }

    fun mapByNumber(publishType: PublishType) = list(publishType).associateBy(TrackLayoutTrackNumber::number)

    fun find(trackNumber: TrackNumber, publishType: PublishType): List<TrackLayoutTrackNumber> {
        logger.serviceCall("find", "trackNumber" to trackNumber, "publishType" to publishType)
        return dao.list(trackNumber, publishType)
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

        return geocodingService.getGeocodingContextCreateResult(publishType, trackNumberId)?.let { contextResult ->
            extractTrackKmLengths(contextResult.geocodingContext, contextResult)
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
            .flatMap { trackNumberId -> (getKmLengths(publishType, trackNumberId) ?: emptyList()).stream() }
            .sorted(compareBy { kmLengthDetails -> kmLengthDetails.trackNumber })
            .collect(Collectors.toList())

        return asCsvFile(kmLengths)
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
            "boundingBox" to boundingBox,
        )
        return get(publishType, trackNumberId)?.let { trackNumber ->
            val referenceLine = referenceLineService.getByTrackNumber(publishType, trackNumberId)
                ?: throw NoSuchEntityException("No ReferenceLine for TrackNumber", trackNumberId)
            val geocodingContext = geocodingService.getGeocodingContext(publishType, trackNumberId)
            if (geocodingContext != null && referenceLine.alignmentVersion != null) {
                alignmentService.getGeometryMetadataSections(
                    referenceLine.alignmentVersion,
                    trackNumber.externalId,
                    boundingBox,
                    geocodingContext,
                )
            } else null
        } ?: listOf()
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

private fun extractTrackKmLengths(
    context: GeocodingContext,
    contextResult: GeocodingContextCreateResult,
): List<TrackLayoutKmLengthDetails> {
    val distances = getKmPostDistances(context, contextResult.validKmPosts)
    val referenceLineLength = context.referenceLineGeometry.length
    val trackNumber = context.trackNumber
    val startPoint = context.referenceLineAddresses.startPoint

    //First km post is usually on another reference line, and therefore it has to be generated here
    return listOf(
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

private fun getKmPostDistances(
    context: GeocodingContext,
    kmPosts: List<TrackLayoutKmPost>,
): List<Pair<TrackLayoutKmPost, Double>> = kmPosts.map { kmPost ->
    val distance = kmPost.location?.let { loc -> context.getM(loc)?.first }
    checkNotNull(distance) { "Couldn't calculated distance for km post, id=${kmPost.id} location=${kmPost.location}" }
    kmPost to distance
}
