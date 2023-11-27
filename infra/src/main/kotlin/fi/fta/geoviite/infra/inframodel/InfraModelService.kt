package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.codeDictionary.CodeDictionaryService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.InframodelParsingException
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geography.GeographyService
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.localization.LocalizationParams
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.GeometryPlanLayout
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.util.LocalizationKey
import fi.fta.geoviite.infra.util.normalizeLinebreaksToUnixFormat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile

const val VALIDATION_LAYOUT_POINTS_RESOLUTION = 10

val noFileValidationError = ParsingError(LocalizationKey(INFRAMODEL_PARSING_KEY_EMPTY))

fun noFileValidationResponse(overrideParameters: OverrideParameters?) = ValidationResponse(
    validationErrors = listOf(noFileValidationError),
    geometryPlan = null,
    planLayout = null,
    source = overrideParameters?.source ?: PlanSource.GEOMETRIAPALVELU,
)

@Service
class InfraModelService @Autowired constructor(
    private val geometryService: GeometryService,
    private val layoutCache: PlanLayoutCache,
    private val geometryDao: GeometryDao,
    private val codeDictionaryService: CodeDictionaryService,
    private val geographyService: GeographyService,
    private val switchLibraryService: SwitchLibraryService,
    private val trackNumberService: LayoutTrackNumberService,
    private val coordinateTransformationService: CoordinateTransformationService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Transactional
    fun saveInfraModel(file: MultipartFile, overrides: OverrideParameters?, extraInfo: ExtraInfoParameters?) =
        saveInfraModel(toInfraModelFile(file, overrides?.encoding?.charset), overrides, extraInfo)

    @Transactional
    fun saveInfraModel(
        file: InfraModelFile,
        overrides: OverrideParameters?,
        extraInfo: ExtraInfoParameters?,
    ): RowVersion<GeometryPlan> {
        logger.serviceCall(
            "saveInfraModel",
            "file.name" to file.name, "overrides" to overrides, "extraInfo" to extraInfo
        )

        val geometryPlan = parseInfraModel(file, overrides, extraInfo)
        val transformedBoundingBox = geometryPlan.units.coordinateSystemSrid
            ?.let { planSrid -> coordinateTransformationService.getTransformation(planSrid, LAYOUT_SRID) }
            ?.let { transformation -> getBoundingPolygonPointsFromAlignments(geometryPlan.alignments, transformation) }

        checkForDuplicateFile(file, geometryPlan.source)

        return geometryDao.insertPlan(geometryPlan, file, transformedBoundingBox)
    }

    @Transactional(readOnly = true)
    fun parseInfraModel(
        file: InfraModelFile,
        overrides: OverrideParameters? = null,
        extraInfo: ExtraInfoParameters? = null,
    ): GeometryPlan {
        logger.serviceCall(
            "parseInfraModel",
            "file.name" to file.name, "overrides" to overrides, "extraInfo" to extraInfo
        )
        val switchStructuresByType = switchLibraryService.getSwitchStructures().associateBy { it.type }
        val trackNumberIdsByNumber = trackNumberService.list(OFFICIAL).associate { tn -> tn.number to tn.id as IntId }

        val parsed = parseInfraModelFile(
            overrides?.source ?: PlanSource.GEOMETRIAPALVELU,
            file,
            geographyService.getCoordinateSystemNameToSridMapping(),
            switchStructuresByType,
            switchLibraryService.getInframodelAliases(),
            trackNumberIdsByNumber,
        )
        return overrideGeometryPlanWithParameters(parsed, overrides, extraInfo)
    }

    fun validateInfraModelFile(
        multipartFile: MultipartFile,
        overrideParameters: OverrideParameters?,
    ): ValidationResponse {
        logger.serviceCall(
            "validateInfraModelFile",
            "file.originalFilename" to multipartFile.originalFilename,
            "overrideParameters" to overrideParameters
        )
        return tryParsing(overrideParameters?.source) {
            val imFile = toInfraModelFile(multipartFile, overrideParameters?.encoding?.charset)
            validateInternal(imFile, overrideParameters)
        }
    }

    fun validateInfraModelFile(
        file: InfraModelFile,
        overrideParameters: OverrideParameters?
    ): ValidationResponse {
        logger.serviceCall(
            "validateInfraModelFile",
            "file.name" to file.name, "overrideParameters" to overrideParameters
        )
        return tryParsing(overrideParameters?.source) { validateInternal(file, overrideParameters) }
    }

    private fun validateInternal(file: InfraModelFile, overrides: OverrideParameters?): ValidationResponse {
        val geometryPlan = parseInfraModel(file, overrides)
        return validateAndTransformToLayoutPlan(geometryPlan)
    }

    @Transactional(readOnly = true)
    fun validateGeometryPlan(
        planId: IntId<GeometryPlan>,
        overrideParameters: OverrideParameters?,
    ): ValidationResponse {
        logger.serviceCall(
            "validateGeometryPlan",
            "planId" to planId,
            "overrideParameters" to overrideParameters
        )

        val geometryPlan = geometryService.getGeometryPlan(planId)
        val planWithParameters = overrideGeometryPlanWithParameters(geometryPlan, overrideParameters)
        return validateAndTransformToLayoutPlan(planWithParameters)
    }

    private fun validateAndTransformToLayoutPlan(plan: GeometryPlan): ValidationResponse {
        val (planLayout: GeometryPlanLayout?, layoutCreationError: TransformationError?) = layoutCache.transformToLayoutPlan(
            geometryPlan = plan,
            includeGeometryData = true,
            pointListStepLength = VALIDATION_LAYOUT_POINTS_RESOLUTION,
        )
        val validationErrors = validateGeometryPlanContent(plan) + listOfNotNull(layoutCreationError)
        return ValidationResponse(validationErrors, plan, planLayout?.withLayoutGeometry(), plan.source)
    }

    @Transactional
    fun updateInfraModel(
        planId: IntId<GeometryPlan>,
        overrideParameters: OverrideParameters?,
        extraInfoParameters: ExtraInfoParameters?,
    ): GeometryPlan {
        logger.serviceCall(
            "updateInfraModel",
            "overrideParameters" to overrideParameters,
            "extraInfoParameters" to extraInfoParameters,
        )

        val geometryPlan = geometryService.getGeometryPlan(planId)
        val overriddenPlan = overrideGeometryPlanWithParameters(geometryPlan, overrideParameters, extraInfoParameters)

        if (overriddenPlan.source != geometryPlan.source) {
            checkForDuplicateFile(geometryService.getPlanFile(planId), overriddenPlan.source)
        }

        return geometryDao.updatePlan(planId, overriddenPlan)
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

        val application = geometryDao.findApplication(plan.application.name, plan.application.version)
            ?: plan.application

        val overrideCs = overrideParameters?.coordinateSystemSrid?.let(geographyService::getCoordinateSystem)

        val messageWithNormalizedLinebreaks = extraInfoParameters
            ?.message?.let { normalizeLinebreaksToUnixFormat(extraInfoParameters.message) }

        // Nullable fields that do not contain a default parameter via the elvis-operator are considered to be assignable
        // to null even if they have non-null values stored in the database.
        return plan.copy(
            units = plan.units.copy(
                coordinateSystemSrid = overrideCs?.srid ?: plan.units.coordinateSystemSrid,
                coordinateSystemName = overrideCs?.name ?: plan.units.coordinateSystemName,
                verticalCoordinateSystem = overrideParameters?.verticalCoordinateSystem
                    ?: plan.units.verticalCoordinateSystem,
            ),
            trackNumberId = overrideParameters?.trackNumberId ?: plan.trackNumberId,
            project = planProject,
            author = planAuthor,
            application = application,
            planPhase = extraInfoParameters?.planPhase,
            decisionPhase = extraInfoParameters?.decisionPhase,
            measurementMethod = extraInfoParameters?.measurementMethod,
            elevationMeasurementMethod = extraInfoParameters?.elevationMeasurementMethod,
            message = messageWithNormalizedLinebreaks ?: plan.message,
            planTime = overrideParameters?.createdDate ?: plan.planTime,
            uploadTime = plan.uploadTime,
            source = overrideParameters?.source ?: plan.source,
        )
    }

    private fun validateGeometryPlanContent(geometryPlan: GeometryPlan): List<ValidationError> {
        return validate(
            geometryPlan,
            codeDictionaryService.getFeatureTypes(),
            switchLibraryService.getSwitchStructuresById(),
        )
    }

    private fun checkForDuplicateFile(planFile: InfraModelFile, source: PlanSource) {
        geometryService.fetchDuplicateGeometryPlanHeader(planFile, source)?.also { duplicate ->
            throw InframodelParsingException(
                message = "InfraModel file exists already",
                localizedMessageKey = "$INFRAMODEL_PARSING_KEY_PARENT.duplicate-inframodel-file-content",
                localizedMessageParams = LocalizationParams("fileName" to duplicate.fileName),
            )
        }
    }
}
