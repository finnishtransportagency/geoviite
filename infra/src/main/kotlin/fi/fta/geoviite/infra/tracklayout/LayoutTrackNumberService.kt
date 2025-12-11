package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geocoding.GeocodingContext
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
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.util.CsvEntry
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.mapNonNullValues
import fi.fta.geoviite.infra.util.printCsv
import java.time.Instant
import java.util.stream.Collectors
import org.springframework.transaction.annotation.Transactional

const val KM_LENGTHS_CSV_TRANSLATION_PREFIX = "data-products.km-lengths.data"

enum class KmLengthsLocationPrecision {
    PRECISE_LOCATION,
    APPROXIMATION_IN_LAYOUT,
}

@GeoviiteService
class LayoutTrackNumberService(
    dao: LayoutTrackNumberDao,
    private val referenceLineService: ReferenceLineService,
    private val geocodingService: GeocodingService,
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

    fun idMatches(
        layoutContext: LayoutContext,
        searchTerm: FreeText,
        onlyIds: Collection<IntId<LayoutTrackNumber>>? = null,
    ): ((term: String, item: LayoutTrackNumber) -> Boolean) = idMatches(dao, layoutContext, searchTerm, onlyIds)

    override fun contentMatches(term: String, item: LayoutTrackNumber, includeDeleted: Boolean) =
        (includeDeleted || item.exists) && item.number.toString().replace("  ", " ").contains(term, true)

    fun mapById(context: LayoutContext): Map<IntId<LayoutTrackNumber>, LayoutTrackNumber> =
        list(context).associateBy { tn -> tn.id as IntId }

    fun find(context: LayoutContext, trackNumber: TrackNumber): List<LayoutTrackNumber> {
        return dao.list(context, trackNumber)
    }

    fun getKmLengths(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
    ): List<LayoutKmLengthDetails>? {
        val oid = getExternalIdsByBranch(trackNumberId).get(layoutContext.branch)
        return geocodingService.getGeocodingContext(layoutContext, trackNumberId)?.let { context ->
            context.kms.map { km ->
                val kmPost = context.kmPosts.find { kmp -> kmp.kmNumber == km.kmNumber }
                LayoutKmLengthDetails(
                    trackNumber = context.trackNumber,
                    trackNumberOid = oid,
                    kmNumber = km.kmNumber,
                    // The first KM might not start at 0 meters -> the km start (KmPost) is on the previous TrackNumber
                    startM = roundTo3Decimals(km.referenceLineM.min) - km.startMeters,
                    endM = roundTo3Decimals(km.referenceLineM.max),
                    layoutLocation = kmPost?.layoutLocation ?: context.referenceLineGeometry.start?.toPoint(),
                    gkLocation = kmPost?.gkLocation,
                    layoutGeometrySource =
                        when {
                            kmPost == null -> GeometrySource.GENERATED
                            kmPost.sourceId == null -> GeometrySource.IMPORTED
                            else -> GeometrySource.PLAN
                        },
                    gkLocationLinkedFromGeometry = kmPost?.sourceId != null,
                )
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
    fun getReferenceLinePolygon(
        layoutContext: LayoutContext,
        trackNumberId: IntId<LayoutTrackNumber>,
        startKm: KmNumber?,
        endKm: KmNumber?,
        bufferSize: Double = ALIGNMENT_POLYGON_BUFFER,
    ): Polygon? {
        val geometry = referenceLineService.getByTrackNumberWithGeometry(layoutContext, trackNumberId)?.second
        val geocodingContext = geocodingService.getGeocodingContext(layoutContext, trackNumberId)

        return if (
            geometry != null && geocodingContext != null && cropIsWithinReferenceLine(startKm, endKm, geocodingContext)
        ) {
            getCropMRange(geocodingContext, Range(LineM(0), geometry.length), startKm, endKm)
                ?.let { cropRange -> cropAlignment(geometry.segmentsWithM, cropRange) }
                ?.let { a -> toPolygon(a, bufferSize) }
        } else {
            null
        }
    }

    fun getExternalIdChangeTime(): Instant = dao.getExternalIdChangeTime()

    @Transactional(readOnly = true)
    fun getExternalIdsByBranch(id: IntId<LayoutTrackNumber>): Map<LayoutBranch, Oid<LayoutTrackNumber>> =
        mapNonNullValues(dao.fetchExternalIdsByBranch(id)) { (_, v) -> v.oid }

    @Transactional
    override fun deleteDraft(branch: LayoutBranch, id: IntId<LayoutTrackNumber>): LayoutRowVersion<LayoutTrackNumber> =
        deleteDraft(branch, id, noUpdateLocationTracks = setOf())

    fun deleteDraft(
        branch: LayoutBranch,
        id: IntId<LayoutTrackNumber>,
        noUpdateLocationTracks: Set<IntId<LocationTrack>>,
    ): LayoutRowVersion<LayoutTrackNumber> {
        referenceLineService.deleteDraftByTrackNumberId(branch, id)
        return super.deleteDraft(branch, id).also { v ->
            locationTrackService.updateDependencies(branch, noUpdateLocationTracks, trackNumberId = v.id)
        }
    }

    @Transactional
    fun saveDraft(branch: LayoutBranch, draftAsset: LayoutTrackNumber): LayoutRowVersion<LayoutTrackNumber> =
        saveDraftInternal(branch, draftAsset, NoParams.instance)

    @Transactional
    override fun saveDraftInternal(
        branch: LayoutBranch,
        draftAsset: LayoutTrackNumber,
        params: NoParams,
    ): LayoutRowVersion<LayoutTrackNumber> =
        super.saveDraftInternal(branch, draftAsset, NoParams.instance).also { v ->
            locationTrackService.updateDependencies(branch, trackNumberId = v.id, noUpdateLocationTracks = setOf())
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
                "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.track-number-oid" to { it.trackNumberOid },
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
            ?.let(LocalizationKey::of)
}

private fun gkLocationConfirmedTranslationKey(confirmed: Boolean): LocalizationKey =
    when {
        confirmed -> "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.confirmed"
        else -> "$KM_LENGTHS_CSV_TRANSLATION_PREFIX.not-confirmed"
    }.let(LocalizationKey::of)

private fun getLocationByPrecision(kmPost: LayoutKmLengthDetails, precision: KmLengthsLocationPrecision): IPoint? =
    when (precision) {
        KmLengthsLocationPrecision.PRECISE_LOCATION -> kmPost.gkLocation?.location
        KmLengthsLocationPrecision.APPROXIMATION_IN_LAYOUT -> kmPost.layoutLocation
    }

private fun getCropMRange(
    context: GeocodingContext<ReferenceLineM>,
    origRange: Range<LineM<ReferenceLineM>>,
    startKm: KmNumber?,
    endKm: KmNumber?,
): Range<LineM<ReferenceLineM>>? {
    val start = startKm?.let { context.kms.find { it.kmNumber >= startKm } }?.referenceLineM?.min
    val end = endKm?.let { context.kms.find { it.kmNumber >= endKm } }?.referenceLineM?.max
    return if (start != null && start >= origRange.max || end != null && end <= origRange.min) {
        null
    } else {
        Range(
            start?.coerceIn(origRange.min, origRange.max) ?: origRange.min,
            end?.coerceIn(origRange.min, origRange.max) ?: origRange.max,
        )
    }
}
