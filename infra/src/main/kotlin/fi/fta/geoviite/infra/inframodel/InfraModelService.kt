package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.codeDictionary.CodeDictionaryService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.HasLocalizeMessageKey
import fi.fta.geoviite.infra.error.InframodelParsingException
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geography.GeographyService
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.GeometryPlanLayout
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.util.LocalizationKey
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

const val VALIDATION_LAYOUT_POINTS_RESOLUTION = 10

@Service
class InfraModelService @Autowired constructor(
    private val geometryService: GeometryService,
    private val layoutCache: PlanLayoutCache,
    private val geometryDao: GeometryDao,
    private val codeDictionaryService: CodeDictionaryService,
    private val geographyService: GeographyService,
    private val switchLibraryService: SwitchLibraryService,
    private val trackNumberService: LayoutTrackNumberService,
    private val coordinateTransformationService: CoordinateTransformationService
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun saveInfraModel(
        file: MultipartFile,
        overrideParameters: OverrideParameters?,
        extraInfoParameters: ExtraInfoParameters?,
    ): RowVersion<GeometryPlan> {
        logger.serviceCall(
            "saveInfraModel",
            "file.originalFilename" to file.originalFilename,
            "overrideParameters" to overrideParameters,
            "extraInfoParameters" to extraInfoParameters,
        )

        val (geometryPlan, imFile) = parseInfraModel(file.bytes, file.originalFilename ?: file.name, file.contentType, overrideParameters, extraInfoParameters)
        val transformedBoundingBox = geometryPlan.units.coordinateSystemSrid
            ?.let { planSrid -> coordinateTransformationService.getTransformation(planSrid, LAYOUT_SRID) }
            ?.let { transformation -> getBoundingPolygonPointsFromAlignments(geometryPlan.alignments, transformation) }

        checkForDuplicateFile(imFile, geometryPlan.source)

        return geometryDao.insertPlan(geometryPlan, imFile, transformedBoundingBox)
    }

    fun parseInfraModel(
        file: ByteArray,
        filename: String,
        contentType: String?,
        overrideParameters: OverrideParameters? = null,
        extraInfoParameters: ExtraInfoParameters? = null,
    ): Pair<GeometryPlan, InfraModelFile> {
        logger.serviceCall(
            "parseInfraModel",
            "filename" to filename,
            "overrideParameters" to overrideParameters,
            "extraInfoParameters" to extraInfoParameters,
        )
        checkForEmptyFileAndIncorrectFileType(file, contentType, filename, MediaType.APPLICATION_XML, MediaType.TEXT_XML)
        val switchStructuresByType = switchLibraryService.getSwitchStructures().associateBy { it.type }
        val trackNumberIdsByNumber = trackNumberService.listOfficial().associate { tn -> tn.number to tn.id as IntId }

        val (parsed, imFile) = parseGeometryPlan(
            PlanSource.GEOMETRIAPALVELU,
            file,
            filename,
            overrideParameters?.encoding?.charset,
            geographyService.getCoordinateSystemNameToSridMapping(),
            switchStructuresByType,
            switchLibraryService.getInframodelAliases(),
            trackNumberIdsByNumber,
        )
        return overrideGeometryPlanWithParameters(parsed, overrideParameters, extraInfoParameters) to imFile
    }

    fun validateInfraModelFile(
        file: MultipartFile,
        overrideParameters: OverrideParameters?
    ): ValidationResponse {
        logger.serviceCall("validateInfraModelFile", "overrideParameters" to overrideParameters)

        val geometryPlan = try {
            parseInfraModel(file.bytes, file.originalFilename ?: file.name, file.contentType, overrideParameters).first
        } catch (e: Exception) {
            logger.warn("Failed to parse InfraModel", e)
            return ValidationResponse(
                validationErrors = listOf(ParsingError(
                    if (e is HasLocalizeMessageKey) e.localizedMessageKey
                    else LocalizationKey(INFRAMODEL_PARSING_KEY_GENERIC)
                )),
                geometryPlan = null,
                planLayout = null,
                source = overrideParameters?.source ?: PlanSource.GEOMETRIAPALVELU
            )
        }

        return validateAndTransformToLayoutPlan(geometryPlan)
    }

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
        return plan.copy(
            oid = extraInfoParameters?.oid ?: plan.oid,
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
            planPhase = extraInfoParameters?.planPhase ?: plan.planPhase,
            decisionPhase = extraInfoParameters?.decisionPhase ?: plan.decisionPhase,
            measurementMethod = extraInfoParameters?.measurementMethod ?: plan.measurementMethod,
            message = extraInfoParameters?.message ?: plan.message,
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
        geometryService.fetchDuplicateGeometryPlanHeader(planFile, source)?.also {
            throw InframodelParsingException(
                message = "InfraModel file exists already",
                localizedMessageKey = "$INFRAMODEL_PARSING_KEY_PARENT.duplicate-inframodel-file-content",
                localizedMessageParams = listOf(it.fileName.toString()),
            )
        }
    }
}
