package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingContextCreateResult
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.util.CsvEntry
import fi.fta.geoviite.infra.util.printCsv
import org.springframework.transaction.annotation.Transactional
import java.util.stream.Collectors

const val KM_LENGTHS_CSV_TRANSLATION_PREFIX = "data-products.km-lengths.csv"

@GeoviiteService
class LayoutTrackNumberService(
    dao: LayoutTrackNumberDao,
    private val referenceLineService: ReferenceLineService,
    private val geocodingService: GeocodingService,
    private val alignmentService: LayoutAlignmentService,
    private val localizationService: LocalizationService,
) : LayoutAssetService<TrackLayoutTrackNumber, LayoutTrackNumberDao>(dao) {

    @Transactional
    fun insert(
        branch: LayoutBranch,
        saveRequest: TrackNumberSaveRequest,
    ): IntId<TrackLayoutTrackNumber> {
        val draftSaveResponse = saveDraftInternal(
            branch,
            TrackLayoutTrackNumber(
                number = saveRequest.number,
                description = saveRequest.description,
                state = saveRequest.state,
                externalId = null,
                contextData = LayoutContextData.newDraft(branch),
            )
        )
        referenceLineService.addTrackNumberReferenceLine(branch, draftSaveResponse.id, saveRequest.startAddress)
        return draftSaveResponse.id
    }

    @Transactional
    fun update(
        branch: LayoutBranch,
        id: IntId<TrackLayoutTrackNumber>,
        saveRequest: TrackNumberSaveRequest,
    ): IntId<TrackLayoutTrackNumber> {
        val original = dao.getOrThrow(branch.draft, id)
        val draftSaveResponse = saveDraftInternal(
            branch,
            original.copy(
                number = saveRequest.number,
                description = saveRequest.description,
                state = saveRequest.state,
            )
        )
        referenceLineService.updateTrackNumberReferenceLine(branch, id, saveRequest.startAddress)
        return draftSaveResponse.id
    }

    @Transactional
    fun updateExternalId(
        branch: LayoutBranch,
        id: IntId<TrackLayoutTrackNumber>,
        oid: Oid<TrackLayoutTrackNumber>,
    ): DaoResponse<TrackLayoutTrackNumber> {
        val original = dao.getOrThrow(branch.draft, id)
        val trackLayoutTrackNumber = original.copy(externalId = oid)

        return saveDraftInternal(branch, trackLayoutTrackNumber)
    }

    @Transactional
    fun deleteDraftAndReferenceLine(
        branch: LayoutBranch,
        id: IntId<TrackLayoutTrackNumber>,
    ): IntId<TrackLayoutTrackNumber> {
        referenceLineService.deleteDraftByTrackNumberId(branch, id)
        return deleteDraft(branch, id).id
    }

    override fun idMatches(term: String, item: TrackLayoutTrackNumber) =
        item.externalId.toString() == term || item.id.toString() == term

    override fun contentMatches(term: String, item: TrackLayoutTrackNumber) =
        item.exists && item.number.toString().replace("  ", " ").contains(term, true)

    fun mapById(context: LayoutContext): Map<IntId<TrackLayoutTrackNumber>, TrackLayoutTrackNumber> =
        list(context).associateBy { tn -> tn.id as IntId }

    fun find(context: LayoutContext, trackNumber: TrackNumber): List<TrackLayoutTrackNumber> {
        return dao.list(context, trackNumber)
    }

    fun getKmLengths(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): List<TrackLayoutKmLengthDetails>? {
        return geocodingService.getGeocodingContextCreateResult(layoutContext, trackNumberId)
            ?.let { contextResult -> extractTrackKmLengths(contextResult.geocodingContext, contextResult) }
    }

    fun getKmLengthsAsCsv(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        startKmNumber: KmNumber? = null,
        endKmNumber: KmNumber? = null,
        lang: LocalizationLanguage,
    ): String {
        val kmLengths = getKmLengths(layoutContext, trackNumberId) ?: emptyList()

        val filteredKmLengths = kmLengths.filter { kmPost ->
            val start = startKmNumber ?: kmLengths.first().kmNumber
            val end = endKmNumber ?: kmLengths.last().kmNumber
            kmPost.kmNumber in start..end
        }

        return asCsvFile(filteredKmLengths, localizationService.getLocalization(lang))
    }

    fun getAllKmLengthsAsCsv(
        layoutContext: LayoutContext,
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
        lang: LocalizationLanguage,
    ): String {
        val kmLengths = trackNumberIds
            .parallelStream()
            .flatMap { trackNumberId -> (getKmLengths(layoutContext, trackNumberId) ?: emptyList()).stream() }
            .sorted(compareBy { kmLengthDetails -> kmLengthDetails.trackNumber })
            .collect(Collectors.toList())

        return asCsvFile(kmLengths, localizationService.getLocalization(lang))
    }

    @Transactional(readOnly = true)
    fun getMetadataSections(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        boundingBox: BoundingBox?,
    ): List<AlignmentPlanSection> {
        return get(layoutContext, trackNumberId)?.let { trackNumber ->
            val referenceLine = referenceLineService.getByTrackNumber(layoutContext, trackNumberId)
                ?: throw NoSuchEntityException("No ReferenceLine for TrackNumber", trackNumberId)
            val geocodingContext = geocodingService.getGeocodingContext(layoutContext, trackNumberId)
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
