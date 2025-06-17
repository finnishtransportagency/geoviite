package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingContextCreateResult
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.CoordinateSystem
import fi.fta.geoviite.infra.geography.GeographyService
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.linking.switches.cropAlignment
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.map.ALIGNMENT_POLYGON_BUFFER
import fi.fta.geoviite.infra.map.toPolygon
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.util.CsvEntry
import fi.fta.geoviite.infra.util.mapNonNullValues
import fi.fta.geoviite.infra.util.printCsv
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.stream.Collectors

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
    private val locationTrackService: LocationTrackService,
) : LayoutAssetService<LayoutTrackNumber, NoParams, LayoutTrackNumberDao>(dao) {

    @Transactional
    fun insert(branch: LayoutBranch, saveRequest: TrackNumberSaveRequest): LayoutRowVersion<LayoutTrackNumber> {
        val draftSaveResponse =
            saveDraft(
                branch,
                LayoutTrackNumber(
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
        id: IntId<LayoutTrackNumber>,
        saveRequest: TrackNumberSaveRequest,
    ): LayoutRowVersion<LayoutTrackNumber> {
        val original = dao.getOrThrow(branch.draft, id)
        val draftSaveResponse =
            saveDraft(
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
    fun insertExternalId(branch: LayoutBranch, id: IntId<LayoutTrackNumber>, oid: Oid<LayoutTrackNumber>) =
        dao.insertExternalId(id, branch, oid)

    @Transactional
    fun deleteDraftAndReferenceLine(branch: LayoutBranch, id: IntId<LayoutTrackNumber>): IntId<LayoutTrackNumber> {
        referenceLineService.deleteDraftByTrackNumberId(branch, id)
        return deleteDraft(branch, id).id
    }

    fun idMatches(
        layoutContext: LayoutContext,
        possibleIds: List<IntId<LayoutTrackNumber>>? = null,
    ): ((term: String, item: LayoutTrackNumber) -> Boolean) =
        dao.fetchExternalIds(layoutContext.branch, possibleIds).let { externalIds ->
            { term, item -> externalIds[item.id]?.oid?.toString() == term || item.id.toString() == term }
        }

    override fun contentMatches(term: String, item: LayoutTrackNumber) =
        item.exists && item.number.toString().replace("  ", " ").contains(term, true)

    fun mapById(context: LayoutContext): Map<IntId<LayoutTrackNumber>, LayoutTrackNumber> =
        list(context).associateBy { tn -> tn.id as IntId }

    fun find(context: LayoutContext, trackNumber: TrackNumber): List<LayoutTrackNumber> {
        return dao.list(context, trackNumber)
    }

    fun getKmLengths(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
    ): List<LayoutKmLengthDetails>? {
        return geocodingService.getGeocodingContextCreateResult(layoutContext, trackNumberId)?.let { contextResult ->
            contextResult.geocodingContext.referenceLineAddresses?.startPoint?.let { startPoint ->
                extractTrackKmLengths(contextResult.geocodingContext, contextResult, startPoint)
            }
        }
    }

    fun getKmLengthsAsCsv(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
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
        trackNumberIds: List<IntId<LayoutTrackNumber>>,
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
        trackNumberId: IntId<LayoutTrackNumber>,
        boundingBox: BoundingBox?,
    ): List<AlignmentPlanSection> {
        return get(layoutContext, trackNumberId)?.let { trackNumber ->
            val referenceLine = referenceLineService.getByTrackNumberOrThrow(layoutContext, trackNumberId)
            val geocodingContext = geocodingService.getGeocodingContext(layoutContext, trackNumberId)

            if (geocodingContext != null && referenceLine.alignmentVersion != null) {
                alignmentService.getGeometryMetadataSections(
                    referenceLine.alignmentVersion,
                    dao.fetchExternalId(layoutContext.branch, trackNumberId)?.oid,
                    boundingBox,
                    geocodingContext,
                )
            } else {
                null
            }
        } ?: listOf()
    }

    @Transactional(readOnly = true)
    fun getReferenceLinePolygon(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
        startKm: KmNumber?,
        endKm: KmNumber?,
        bufferSize: Double = ALIGNMENT_POLYGON_BUFFER,
    ): List<IPoint> {
        val alignment = referenceLineService.getByTrackNumberWithAlignment(layoutContext, trackNumberId)?.second
        val geocodingContext = geocodingService.getGeocodingContext(layoutContext, trackNumberId)

        return if (
            alignment == null ||
                geocodingContext == null ||
                !cropIsWithinReferenceLine(startKm, endKm, geocodingContext)
        ) {
            emptyList()
        } else {
            getCropMRange(geocodingContext, Range(0.0, alignment.length), startKm, endKm)?.let { cropRange ->
                toPolygon(cropAlignment(alignment.segmentsWithM, cropRange))
            } ?: emptyList()
        }
    }

    fun getExternalIdChangeTime(): Instant = dao.getExternalIdChangeTime()

    @Transactional(readOnly = true)
    fun getExternalIdsByBranch(id: IntId<LayoutTrackNumber>): Map<LayoutBranch, Oid<LayoutTrackNumber>> =
        mapNonNullValues(dao.fetchExternalIdsByBranch(id)) { (_, v) -> v.oid }

    @Transactional
    fun saveDraft(branch: LayoutBranch, draftAsset: LayoutTrackNumber): LayoutRowVersion<LayoutTrackNumber> =
        saveDraftInternal(branch, draftAsset, NoParams.instance).also { v ->
            locationTrackService.updateDependencies(branch, trackNumberId = v.id)
        }
}

private fun asCsvFile(
    items: List<LayoutKmLengthDetails>,
    precision: KmLengthsLocationPrecision,
    translation: Translation,
    getCoordinateSystem: (srid: Srid) -> CoordinateSystem,
): String {
    val columns =
        mapOf<String, (item: LayoutKmLengthDetails) -> Any?>(
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

private fun isGeneratedRow(kmPost: LayoutKmLengthDetails): Boolean =
    kmPost.layoutGeometrySource == GeometrySource.GENERATED

private fun locationSourceTranslationKey(
    kmPost: LayoutKmLengthDetails,
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

private fun getLocationByPrecision(kmPost: LayoutKmLengthDetails, precision: KmLengthsLocationPrecision): IPoint? =
    when (precision) {
        KmLengthsLocationPrecision.PRECISE_LOCATION -> kmPost.gkLocation?.location
        KmLengthsLocationPrecision.APPROXIMATION_IN_LAYOUT -> kmPost.layoutLocation
    }

private fun extractTrackKmLengths(
    context: GeocodingContext,
    contextResult: GeocodingContextCreateResult,
    startPoint: AddressPoint,
): List<LayoutKmLengthDetails> {
    val distances = getKmPostDistances(context, contextResult.validKmPosts)
    val referenceLineLength = context.referenceLineGeometry.length
    val trackNumber = context.trackNumber

    // First km post is usually on another reference line, and therefore it has to be generated here
    return listOf(
        LayoutKmLengthDetails(
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

            LayoutKmLengthDetails(
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
    kmPosts: List<LayoutKmPost>,
): List<Pair<LayoutKmPost, Double>> =
    kmPosts.map { kmPost ->
        val distance = kmPost.layoutLocation?.let { loc -> context.getM(loc)?.first }
        checkNotNull(distance) {
            "Couldn't calculate distance for km post, id=${kmPost.id} location=${kmPost.layoutLocation}"
        }
        kmPost to distance
    }

private fun getCropMRange(
    context: GeocodingContext,
    origRange: Range<Double>,
    startKm: KmNumber?,
    endKm: KmNumber?,
): Range<Double>? {
    val start = startKm?.let { context.referencePoints.find { it.kmNumber >= startKm } }?.distance
    val end = endKm?.let { context.referencePoints.find { it.kmNumber > endKm } }?.distance
    return if (start != null && start >= origRange.max || end != null && end <= origRange.min) {
        null
    } else {
        Range(
            start?.coerceIn(origRange.min, origRange.max) ?: origRange.min,
            end?.coerceIn(origRange.min, origRange.max) ?: origRange.max,
        )
    }
}
