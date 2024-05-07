package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingContextCreateResult
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.util.CsvEntry
import fi.fta.geoviite.infra.util.printCsv
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.stream.Collectors

const val KM_LENGTHS_CSV_TRANSLATION_PREFIX = "data-products.km-lengths.csv"

@Service
class LayoutTrackNumberService(
    dao: LayoutTrackNumberDao,
    private val referenceLineService: ReferenceLineService,
    private val geocodingService: GeocodingService,
    private val alignmentService: LayoutAlignmentService,
    private val localizationService: LocalizationService,
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
                // TODO: GVT-2397
                contextData = LayoutContextData.newDraft(LayoutBranch.main),
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

    override fun idMatches(term: String, item: TrackLayoutTrackNumber) =
        item.externalId.toString() == term || item.id.toString() == term

    override fun contentMatches(term: String, item: TrackLayoutTrackNumber) =
        item.exists && item.number.toString().replace("  ", " ").contains(term, true)

    fun mapById(publicationState: PublicationState) = list(publicationState).associateBy { tn -> tn.id as IntId }

    fun mapByNumber(publicationState: PublicationState) = list(publicationState).associateBy(TrackLayoutTrackNumber::number)

    fun find(trackNumber: TrackNumber, publicationState: PublicationState): List<TrackLayoutTrackNumber> {
        logger.serviceCall("find", "trackNumber" to trackNumber, "publicationState" to publicationState)
        return dao.list(trackNumber, publicationState)
    }

    fun getKmLengths(
        publicationState: PublicationState,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): List<TrackLayoutKmLengthDetails>? {
        logger.serviceCall(
            "getKmLengths",
            "trackNumberId" to trackNumberId,
            "publicationState" to publicationState,
        )

        return geocodingService.getGeocodingContextCreateResult(publicationState, trackNumberId)?.let { contextResult ->
            extractTrackKmLengths(contextResult.geocodingContext, contextResult)
        }
    }

    fun getKmLengthsAsCsv(
        publicationState: PublicationState,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        startKmNumber: KmNumber? = null,
        endKmNumber: KmNumber? = null,
        lang: String,
    ): String {
        logger.serviceCall(
            "getKmLengthsAsCsv",
            "trackNumberId" to trackNumberId,
            "publicationState" to publicationState,
            "startKmNumber" to startKmNumber,
            "endKmNumber" to endKmNumber,
            "lang" to lang,
        )

        val kmLengths = getKmLengths(publicationState, trackNumberId) ?: emptyList()

        val filteredKmLengths = kmLengths.filter { kmPost ->
            val start = startKmNumber ?: kmLengths.first().kmNumber
            val end = endKmNumber ?: kmLengths.last().kmNumber

            kmPost.kmNumber in start..end
        }

        return asCsvFile(filteredKmLengths, localizationService.getLocalization(lang))
    }

    fun getAllKmLengthsAsCsv(
        publicationState: PublicationState,
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
        lang: String,
    ): String {
        val kmLengths = trackNumberIds
            .parallelStream()
            .flatMap { trackNumberId -> (getKmLengths(publicationState, trackNumberId) ?: emptyList()).stream() }
            .sorted(compareBy { kmLengthDetails -> kmLengthDetails.trackNumber })
            .collect(Collectors.toList())

        return asCsvFile(kmLengths, localizationService.getLocalization(lang))
    }

    @Transactional(readOnly = true)
    fun getMetadataSections(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        publicationState: PublicationState,
        boundingBox: BoundingBox?,
    ): List<AlignmentPlanSection> {
        logger.serviceCall(
            "getSectionsByPlan",
            "trackNumberId" to trackNumberId,
            "publicationState" to publicationState,
            "boundingBox" to boundingBox,
        )
        return get(publicationState, trackNumberId)?.let { trackNumber ->
            val referenceLine = referenceLineService.getByTrackNumber(publicationState, trackNumberId)
                ?: throw NoSuchEntityException("No ReferenceLine for TrackNumber", trackNumberId)
            val geocodingContext = geocodingService.getGeocodingContext(publicationState, trackNumberId)
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

private fun asCsvFile(items: List<TrackLayoutKmLengthDetails>, translation: Translation): String {
    val columns = mapOf<String, (item: TrackLayoutKmLengthDetails) -> Any?>(
        "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.track-number" to { it.trackNumber },
        "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.kilometer" to { it.kmNumber },
        "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.station-start" to { it.startM },
        "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.station-end" to { it.endM },
        "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.length" to { it.length },
        "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.location-e" to { it.location?.x?.let(::roundTo3Decimals) },
        "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.location-n" to { it.location?.y?.let(::roundTo3Decimals) },
        "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.warning" to { kmPost ->
            if (kmPost.location != null && kmPost.locationSource == GeometrySource.IMPORTED) {
                translation.t("$KM_LENGTHS_CSV_TRANSLATION_PREFIX.imported-warning")
            } else if (kmPost.location != null && kmPost.locationSource == GeometrySource.GENERATED) {
                translation.t("$KM_LENGTHS_CSV_TRANSLATION_PREFIX.generated-warning")
            } else {
                ""
            }
        },
    ).map { (key, fn) -> CsvEntry(translation.t(key), fn) }

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
            trackNumber = trackNumber,
            kmNumber = startPoint.address.kmNumber,
            startM = roundTo3Decimals(context.startAddress.meters.negate()),
            endM = roundTo3Decimals(distances.firstOrNull()?.second ?: referenceLineLength),
            locationSource = GeometrySource.GENERATED,
            location = startPoint.point.toPoint()
        )
    ) + distances.mapIndexed { index, (kmPost, startM) ->
        val endM = distances.getOrNull(index + 1)?.second ?: referenceLineLength

        TrackLayoutKmLengthDetails(
            trackNumber = trackNumber,
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
