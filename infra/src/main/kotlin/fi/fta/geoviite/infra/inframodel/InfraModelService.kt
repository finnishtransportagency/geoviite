package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.codeDictionary.CodeDictionaryService
import fi.fta.geoviite.infra.codeDictionary.FeatureType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.InframodelParsingException
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geography.GeographyService
import fi.fta.geoviite.infra.geometry.Author
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryPlanHeader
import fi.fta.geoviite.infra.geometry.GeometryService
import fi.fta.geoviite.infra.geometry.GeometryValidationIssue
import fi.fta.geoviite.infra.geometry.PlanApplicability
import fi.fta.geoviite.infra.geometry.PlanLayoutCache
import fi.fta.geoviite.infra.geometry.PlanSource
import fi.fta.geoviite.infra.geometry.Project
import fi.fta.geoviite.infra.geometry.TransformationError
import fi.fta.geoviite.infra.geometry.fileNameWithSourcePrefixIfPaikannuspalvelu
import fi.fta.geoviite.infra.geometry.getBoundingPolygonFromPlan
import fi.fta.geoviite.infra.geometry.validate
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.GeometryPlanLayout
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.util.CsvEntry
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.printCsv
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

const val VALIDATION_LAYOUT_POINTS_RESOLUTION = 10

val noFileValidationError = ParsingError(LocalizationKey.of(INFRAMODEL_PARSING_KEY_EMPTY))
const val START_KM_PARAM_KEY = "startKm"
const val END_KM_PARAM_KEY = "endKm"

fun noFileValidationResponse(overrideParameters: OverrideParameters?) =
    ValidationResponse(
        geometryValidationIssues = listOf(noFileValidationError),
        geometryPlan = null,
        planLayout = null,
        source = overrideParameters?.source ?: PlanSource.GEOMETRIAPALVELU,
    )

@GeoviiteService
class InfraModelService
@Autowired
constructor(
    private val geometryService: GeometryService,
    private val layoutCache: PlanLayoutCache,
    private val geometryDao: GeometryDao,
    private val codeDictionaryService: CodeDictionaryService,
    private val geographyService: GeographyService,
    private val switchLibraryService: SwitchLibraryService,
    private val trackNumberService: LayoutTrackNumberService,
    private val locationTrackService: LocationTrackService,
    private val coordinateTransformationService: CoordinateTransformationService,
) {

    @Transactional
    fun saveInfraModel(file: MultipartFile, overrides: OverrideParameters?, extraInfo: ExtraInfoParameters?) =
        saveInfraModel(toInfraModelFile(file, overrides?.encoding?.charset), overrides, extraInfo)

    @Transactional
    fun saveInfraModel(
        file: InfraModelFile,
        overrides: OverrideParameters?,
        extraInfo: ExtraInfoParameters?,
    ): RowVersion<GeometryPlan> {
        val geometryPlan = cleanMissingFeatureTypeCodes(parseInfraModel(file, overrides, extraInfo))

        checkForDuplicateFile(file.hash, geometryPlan.source)

        return geometryDao.insertPlan(geometryPlan, file, getBoundingPolygon(geometryPlan))
    }

    fun getBoundingPolygon(geometryPlan: GeometryPlan): Polygon? =
        geometryPlan.units.coordinateSystemSrid
            ?.let { planSrid -> coordinateTransformationService.getTransformation(planSrid, LAYOUT_SRID) }
            ?.let { transformation -> getBoundingPolygonFromPlan(geometryPlan, transformation) }

    fun parseInfraModel(
        file: InfraModelFile,
        overrides: OverrideParameters? = null,
        extraInfo: ExtraInfoParameters? = null,
    ): GeometryPlan {
        val switchStructuresByType = switchLibraryService.getSwitchStructures().associateBy { it.type }

        val parsed =
            parseInfraModelFile(
                overrides?.source ?: PlanSource.GEOMETRIAPALVELU,
                file,
                geographyService.getCoordinateSystemNameToSridMapping(),
                switchStructuresByType,
                switchLibraryService.getInframodelAliases(),
            )
        return overrideGeometryPlanWithParameters(parsed, overrides, extraInfo)
    }

    fun validateInfraModelFile(
        multipartFile: MultipartFile,
        overrideParameters: OverrideParameters?,
    ): ValidationResponse {
        return tryParsing(overrideParameters?.source) {
            val imFile = toInfraModelFile(multipartFile, overrideParameters?.encoding?.charset)
            validateInternal(imFile, overrideParameters)
        }
    }

    fun validateInfraModelFile(file: InfraModelFile, overrideParameters: OverrideParameters?): ValidationResponse {
        return tryParsing(overrideParameters?.source) { validateInternal(file, overrideParameters) }
    }

    fun getInfraModelBatchSummary(
        headers: List<GeometryPlanHeader>,
        summaryFileName: FileName,
        translation: Translation,
    ): Pair<FileName, String> =
        summaryFileName to getInfraModelBatchSummaryCsv(headers.sortedBy { it.fileName }, translation)

    private fun validateInternal(file: InfraModelFile, overrides: OverrideParameters?): ValidationResponse {
        val geometryPlan = parseInfraModel(file, overrides)
        return validateAndTransformToLayoutPlan(geometryPlan)
    }

    private fun cleanMissingFeatureTypeCodes(plan: GeometryPlan): GeometryPlan {
        val codes = codeDictionaryService.getFeatureTypes().map(FeatureType::code)
        val cleanedAlignments =
            plan.alignments.map { a ->
                if (a.featureTypeCode != null && a.featureTypeCode !in codes) a.copy(featureTypeCode = null) else a
            }
        return if (cleanedAlignments != plan.alignments) plan.copy(alignments = cleanedAlignments) else plan
    }

    @Transactional(readOnly = true)
    fun validateGeometryPlan(planId: IntId<GeometryPlan>, overrideParameters: OverrideParameters?): ValidationResponse {
        val geometryPlan = geometryService.getGeometryPlan(planId)
        val planWithParameters = overrideGeometryPlanWithParameters(geometryPlan, overrideParameters)
        return validateAndTransformToLayoutPlan(planWithParameters)
    }

    private fun validateAndTransformToLayoutPlan(plan: GeometryPlan): ValidationResponse {
        val (planLayout: GeometryPlanLayout?, layoutCreationError: TransformationError?) =
            layoutCache.transformToLayoutPlan(
                geometryPlan = plan,
                includeGeometryData = true,
                pointListStepLength = VALIDATION_LAYOUT_POINTS_RESOLUTION,
            )
        val validationIssues = validateGeometryPlanContent(plan) + listOfNotNull(layoutCreationError)
        return ValidationResponse(validationIssues, plan, planLayout?.withLayoutGeometry(), plan.source)
    }

    @Transactional
    fun updateInfraModel(
        planId: IntId<GeometryPlan>,
        overrideParameters: OverrideParameters?,
        extraInfoParameters: ExtraInfoParameters?,
    ): RowVersion<GeometryPlan> {
        val geometryPlan = geometryService.getGeometryPlan(planId)
        val overriddenPlan = overrideGeometryPlanWithParameters(geometryPlan, overrideParameters, extraInfoParameters)

        if (overriddenPlan.source != geometryPlan.source) {
            checkForDuplicateFile(geometryService.getPlanFileHash(planId), overriddenPlan.source)
        }

        return geometryDao.updatePlan(planId, overriddenPlan)
    }

    @Transactional
    fun setPlanApplicability(planId: IntId<GeometryPlan>, applicability: PlanApplicability?): RowVersion<GeometryPlan> =
        geometryService.getGeometryPlan(planId).let { plan ->
            geometryDao.updatePlan(planId, plan.copy(planApplicability = applicability))
        }

    fun getInfraModelBatchSummaryCsv(headers: List<GeometryPlanHeader>, translation: Translation): String {
        return printCsv(
            mapOf<String, (item: GeometryPlanHeader) -> Any?>(
                    "file-name" to { fileNameWithSourcePrefixIfPaikannuspalvelu(translation, it.fileName, it.source) },
                    "start-km" to { it.kmNumberRange?.min },
                    "end-km" to { it.kmNumberRange?.max },
                    "crs" to { it.units.coordinateSystemName },
                    "vertical-crs" to { it.units.verticalCoordinateSystem },
                    "plan-time" to { it.planTime },
                    "message" to { it.message },
                )
                .map { (key, fn) -> CsvEntry(translation.t("plan-download.csv.$key"), fn) },
            headers,
        )
    }

    private fun overrideGeometryPlanWithParameters(
        plan: GeometryPlan,
        overrideParameters: OverrideParameters? = null,
        extraInfoParameters: ExtraInfoParameters? = null,
    ): GeometryPlan {
        val planProject =
            if (overrideParameters?.projectId is IntId<Project>) geometryDao.getProject(overrideParameters.projectId)
            else geometryDao.findProject(plan.project.name) ?: plan.project

        val planAuthor =
            if (overrideParameters?.authorId is IntId<Author>) geometryDao.getAuthor(overrideParameters.authorId)
            else plan.author?.let { author -> geometryDao.findAuthor(author.companyName) ?: author }

        val application =
            plan.application?.let { application ->
                geometryDao.findApplication(application.name, application.version) ?: application
            }

        val overrideCs = overrideParameters?.coordinateSystemSrid?.let(geographyService::getCoordinateSystem)

        // Nullable fields that do not contain a default parameter via the elvis-operator are
        // considered to be assignable
        // to null even if they have non-null values stored in the database.
        return plan.copy(
            units =
                plan.units.copy(
                    coordinateSystemSrid = overrideCs?.srid ?: plan.units.coordinateSystemSrid,
                    coordinateSystemName = overrideCs?.name ?: plan.units.coordinateSystemName,
                    verticalCoordinateSystem =
                        overrideParameters?.verticalCoordinateSystem ?: plan.units.verticalCoordinateSystem,
                ),
            trackNumber = overrideParameters?.trackNumber ?: plan.trackNumber,
            project = planProject,
            author = planAuthor,
            application = application,
            planPhase = extraInfoParameters?.planPhase,
            decisionPhase = extraInfoParameters?.decisionPhase,
            measurementMethod = extraInfoParameters?.measurementMethod,
            elevationMeasurementMethod = extraInfoParameters?.elevationMeasurementMethod,
            message = extraInfoParameters?.message ?: plan.message,
            name = extraInfoParameters?.name ?: plan.name,
            planTime = overrideParameters?.createdDate ?: plan.planTime,
            uploadTime = plan.uploadTime,
            source = overrideParameters?.source ?: plan.source,
            planApplicability = extraInfoParameters?.planApplicability,
        )
    }

    private fun validateGeometryPlanContent(geometryPlan: GeometryPlan): List<GeometryValidationIssue> {
        return validate(
            geometryPlan,
            codeDictionaryService.getFeatureTypes(),
            switchLibraryService.getSwitchStructuresById(),
            trackNumberService.list(MainLayoutContext.official).map { it.number },
        )
    }

    private fun checkForDuplicateFile(planFileHash: FileHash, source: PlanSource) {
        geometryService.fetchDuplicateGeometryPlanHeader(planFileHash, source)?.also { duplicate ->
            throw InframodelParsingException(
                message = "InfraModel file exists already",
                localizedMessageKey = "$INFRAMODEL_PARSING_KEY_PARENT.duplicate-inframodel-file-content",
                localizedMessageParams = localizationParams("fileName" to duplicate.fileName),
            )
        }
    }
}
