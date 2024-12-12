package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingContextCreateResult
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.CoordinateSystem
import fi.fta.geoviite.infra.geography.GeographyService
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.util.CsvEntry
import fi.fta.geoviite.infra.util.printCsv
import java.time.Instant
import java.util.stream.Collectors
import org.springframework.transaction.annotation.Transactional

const val KM_LENGTHS_CSV_TRANSLATION_PREFIX = "data-products.km-lengths.csv"

enum class KmLengthsLocationPrecision {
    PRECISE_LOCATION,
    APPROXIMATION_IN_LAYOUT,
}

@GeoviiteService
class LayoutTrackNumberService(
    dao: LayoutTrackNumberDao,
    private val referenceLineService: ReferenceLineService,
    private val geocodingService: GeocodingService,
    private val alignmentService: LayoutAlignmentService,
    private val localizationService: LocalizationService,
    private val geographyService: GeographyService,
) : LayoutAssetService<TrackLayoutTrackNumber, LayoutTrackNumberDao>(dao) {

    @Transactional
    fun insert(branch: LayoutBranch, saveRequest: TrackNumberSaveRequest): LayoutRowVersion<TrackLayoutTrackNumber> {
        val draftSaveResponse =
            saveDraftInternal(
                branch,
                TrackLayoutTrackNumber(
                    number = saveRequest.number,
                    description = saveRequest.description,
                    state = saveRequest.state,
                    contextData = LayoutContextData.newDraft(branch, dao.createId()),
                ),
            )
        referenceLineService.addTrackNumberReferenceLine(branch, draftSaveResponse.id, saveRequest.startAddress)
        return draftSaveResponse
    }

    @Transactional
    fun update(
        branch: LayoutBranch,
        id: IntId<TrackLayoutTrackNumber>,
        saveRequest: TrackNumberSaveRequest,
    ): LayoutRowVersion<TrackLayoutTrackNumber> {
        val original = dao.getOrThrow(branch.draft, id)
        val draftSaveResponse =
            saveDraftInternal(
                branch,
                original.copy(
                    number = saveRequest.number,
                    description = saveRequest.description,
                    state = saveRequest.state,
                ),
            )
        referenceLineService.updateTrackNumberReferenceLine(branch, id, saveRequest.startAddress)
        return draftSaveResponse
    }

    @Transactional
    fun insertExternalId(branch: LayoutBranch, id: IntId<TrackLayoutTrackNumber>, oid: Oid<TrackLayoutTrackNumber>) =
        dao.insertExternalId(id, branch, oid)

    @Transactional
    fun deleteDraftAndReferenceLine(
        branch: LayoutBranch,
        id: IntId<TrackLayoutTrackNumber>,
    ): IntId<TrackLayoutTrackNumber> {
        referenceLineService.deleteDraftByTrackNumberId(branch, id)
        return deleteDraft(branch, id).id
    }

    @Transactional
    override fun cancel(
        branch: DesignBranch,
        id: IntId<TrackLayoutTrackNumber>,
    ): LayoutRowVersion<TrackLayoutTrackNumber>? =
        dao.get(branch.official, id)?.let { trackNumber ->
            referenceLineService.getByTrackNumber(branch.official, trackNumber.id as IntId)?.let {
                referenceLineService.cancel(branch, it.id as IntId)
            }
            super.cancel(branch, id)
        }

    fun idMatches(
        layoutContext: LayoutContext,
        possibleIds: List<IntId<TrackLayoutTrackNumber>>? = null,
    ): ((term: String, item: TrackLayoutTrackNumber) -> Boolean) =
        dao.fetchExternalIds(layoutContext.branch, possibleIds).let { externalIds ->
            { term, item -> externalIds[item.id]?.toString() == term || item.id.toString() == term }
        }

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
        return geocodingService.getGeocodingContextCreateResult(layoutContext, trackNumberId)?.let { contextResult ->
            contextResult.geocodingContext.referenceLineAddresses?.startPoint?.let { startPoint ->
                extractTrackKmLengths(contextResult.geocodingContext, contextResult, startPoint)
            }
        }
    }

    fun getKmLengthsAsCsv(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        startKmNumber: KmNumber? = null,
        endKmNumber: KmNumber? = null,
        precision: KmLengthsLocationPrecision,
        lang: LocalizationLanguage,
    ): String {
        val kmLengths = getKmLengths(layoutContext, trackNumberId) ?: emptyList()

        val filteredKmLengths =
            kmLengths.filter { kmPost ->
                val start = startKmNumber ?: kmLengths.first().kmNumber
                val end = endKmNumber ?: kmLengths.last().kmNumber
                kmPost.kmNumber in start..end
            }

        return asCsvFile(
            filteredKmLengths,
            precision,
            localizationService.getLocalization(lang),
            geographyService::getCoordinateSystem,
        )
    }

    fun getAllKmLengthsAsCsv(
        layoutContext: LayoutContext,
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
        lang: LocalizationLanguage,
    ): String {
        val kmLengths =
            trackNumberIds
                .parallelStream()
                .flatMap { trackNumberId -> (getKmLengths(layoutContext, trackNumberId) ?: emptyList()).stream() }
                .sorted(compareBy { kmLengthDetails -> kmLengthDetails.trackNumber })
                .collect(Collectors.toList())

        return asCsvFile(
            kmLengths,
            KmLengthsLocationPrecision.PRECISE_LOCATION,
            localizationService.getLocalization(lang),
            geographyService::getCoordinateSystem,
        )
    }

    @Transactional(readOnly = true)
    fun getMetadataSections(
        layoutContext: LayoutContext,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        boundingBox: BoundingBox?,
    ): List<AlignmentPlanSection> {
        return get(layoutContext, trackNumberId)?.let { trackNumber ->
            val referenceLine =
                referenceLineService.getByTrackNumber(layoutContext, trackNumberId)
                    ?: throw NoSuchEntityException("No ReferenceLine for TrackNumber", trackNumberId)
            val geocodingContext = geocodingService.getGeocodingContext(layoutContext, trackNumberId)
            if (geocodingContext != null && referenceLine.alignmentVersion != null) {
                alignmentService.getGeometryMetadataSections(
                    referenceLine.alignmentVersion,
                    dao.fetchExternalId(layoutContext.branch, trackNumberId),
                    boundingBox,
                    geocodingContext,
                )
            } else {
                null
            }
        } ?: listOf()
    }

    fun getExternalIdChangeTime(): Instant = dao.getExternalIdChangeTime()

    @Transactional(readOnly = true)
    fun getExternalIdsByBranch(id: IntId<TrackLayoutTrackNumber>): Map<LayoutBranch, Oid<TrackLayoutTrackNumber>> {
        return dao.fetchExternalIdsByBranch(id)
    }
}

private fun asCsvFile(
    items: List<TrackLayoutKmLengthDetails>,
    precision: KmLengthsLocationPrecision,
    translation: Translation,
    getCoordinateSystem: (srid: Srid) -> CoordinateSystem,
): String {
    val columns =
        mapOf<String, (item: TrackLayoutKmLengthDetails) -> Any?>(
                "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.track-number" to { it.trackNumber },
                "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.kilometer" to { it.kmNumber },
                "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.station-start" to { it.startM },
                "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.station-end" to { it.endM },
                "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.length" to { it.length },
                "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.coordinate-system" to
                    {
                        when (precision) {
                            KmLengthsLocationPrecision.PRECISE_LOCATION ->
                                it.gkLocation?.location?.srid?.let(getCoordinateSystem)?.name
                            KmLengthsLocationPrecision.APPROXIMATION_IN_LAYOUT -> getCoordinateSystem(LAYOUT_SRID).name
                        }
                    },
                "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.location-e" to
                    {
                        getLocationByPrecision(it, precision)?.x?.let(::roundTo3Decimals)
                    },
                "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.location-n" to
                    {
                        getLocationByPrecision(it, precision)?.y?.let(::roundTo3Decimals)
                    },
                "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.location-source" to
                    {
                        locationSourceTranslationKey(it, precision)?.let(translation::t) ?: ""
                    },
                "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.location-confirmed" to
                    {
                        if (isGeneratedRow(it)) {
                            ""
                        } else if (precision == KmLengthsLocationPrecision.PRECISE_LOCATION && it.gkLocation != null) {
                            translation.t(gkLocationConfirmedTranslationKey(it.gkLocation.confirmed))
                        } else {
                            translation.t("$KM_LENGTHS_CSV_TRANSLATION_PREFIX.not-confirmed")
                        }
                    },
                "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.warning" to
                    { kmPost ->
                        if (
                            precision == KmLengthsLocationPrecision.APPROXIMATION_IN_LAYOUT &&
                                kmPost.layoutLocation != null &&
                                kmPost.layoutGeometrySource == GeometrySource.IMPORTED
                        ) {
                            translation.t("$KM_LENGTHS_CSV_TRANSLATION_PREFIX.imported-warning")
                        } else if (
                            precision == KmLengthsLocationPrecision.PRECISE_LOCATION &&
                                kmPost.gkLocation?.source == KmPostGkLocationSource.FROM_LAYOUT
                        ) {
                            translation.t("$KM_LENGTHS_CSV_TRANSLATION_PREFIX.imported-warning")
                        } else if (
                            kmPost.layoutLocation != null && kmPost.layoutGeometrySource == GeometrySource.GENERATED
                        ) {
                            translation.t("$KM_LENGTHS_CSV_TRANSLATION_PREFIX.generated-warning")
                        } else {
                            ""
                        }
                    },
            )
            .map { (key, fn) -> CsvEntry(translation.t(key), fn) }

    return printCsv(columns, items)
}

private fun isGeneratedRow(kmPost: TrackLayoutKmLengthDetails): Boolean =
    kmPost.layoutGeometrySource == GeometrySource.GENERATED

private fun locationSourceTranslationKey(
    kmPost: TrackLayoutKmLengthDetails,
    precision: KmLengthsLocationPrecision,
): LocalizationKey? {
    return if (isGeneratedRow(kmPost)) {
        null
    } else
        if (precision == KmLengthsLocationPrecision.PRECISE_LOCATION) {
                kmPost.gkLocation?.source?.let { source -> "enum.KmPostGkLocationSource.$source" }
            } else {
                when (kmPost.gkLocationLinkedFromGeometry) {
                    true -> "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.from-geometry"
                    false -> "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.from-ratko"
                }
            }
            ?.let(::LocalizationKey)
}

private fun gkLocationConfirmedTranslationKey(confirmed: Boolean): LocalizationKey =
    when {
        confirmed -> "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.confirmed"
        else -> "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.not-confirmed"
    }.let(::LocalizationKey)

private fun getLocationByPrecision(kmPost: TrackLayoutKmLengthDetails, precision: KmLengthsLocationPrecision): IPoint? =
    when (precision) {
        KmLengthsLocationPrecision.PRECISE_LOCATION -> kmPost.gkLocation?.location
        KmLengthsLocationPrecision.APPROXIMATION_IN_LAYOUT -> kmPost.layoutLocation
    }

private fun extractTrackKmLengths(
    context: GeocodingContext,
    contextResult: GeocodingContextCreateResult,
    startPoint: AddressPoint,
): List<TrackLayoutKmLengthDetails> {
    val distances = getKmPostDistances(context, contextResult.validKmPosts)
    val referenceLineLength = context.referenceLineGeometry.length
    val trackNumber = context.trackNumber

    // First km post is usually on another reference line, and therefore it has to be generated here
    return listOf(
        TrackLayoutKmLengthDetails(
            trackNumber = trackNumber,
            kmNumber = startPoint.address.kmNumber,
            startM = roundTo3Decimals(context.startAddress.meters.negate()),
            endM = roundTo3Decimals(distances.firstOrNull()?.second ?: referenceLineLength),
            layoutGeometrySource = GeometrySource.GENERATED,
            layoutLocation = startPoint.point.toPoint(),
            gkLocation = null,
            gkLocationLinkedFromGeometry = false,
        )
    ) +
        distances.mapIndexed { index, (kmPost, startM) ->
            val endM = distances.getOrNull(index + 1)?.second ?: referenceLineLength

            TrackLayoutKmLengthDetails(
                trackNumber = trackNumber,
                kmNumber = kmPost.kmNumber,
                startM = roundTo3Decimals(startM),
                endM = roundTo3Decimals(endM),
                layoutLocation = kmPost.layoutLocation,
                gkLocation = kmPost.gkLocation,
                layoutGeometrySource = if (kmPost.sourceId != null) GeometrySource.PLAN else GeometrySource.IMPORTED,
                gkLocationLinkedFromGeometry = kmPost.sourceId != null,
            )
        }
}

private fun getKmPostDistances(
    context: GeocodingContext,
    kmPosts: List<TrackLayoutKmPost>,
): List<Pair<TrackLayoutKmPost, Double>> =
    kmPosts.map { kmPost ->
        val distance = kmPost.layoutLocation?.let { loc -> context.getM(loc)?.first }
        checkNotNull(distance) {
            "Couldn't calculate distance for km post, id=${kmPost.id} location=${kmPost.layoutLocation}"
        }
        kmPost to distance
    }
