package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.codeDictionary.CodeDictionaryService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.HasLocalizeMessageKey
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.error.InframodelParsingException
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


@Service
class InfraModelService @Autowired constructor(
    private val geometryService: GeometryService,
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
        logger.serviceCall("saveInfraModel", "file.originalFilename" to file.originalFilename)

        val (parsedGeometryPlan, imFile) = validateInputFileAndParseInfraModel(file, overrideParameters?.encoding)
        val geometryPlan =
        overrideGeometryPlanWithParameters(parsedGeometryPlan, overrideParameters, extraInfoParameters)
        val transformedBoundingBox = geometryPlan.units.coordinateSystemSrid
            ?.let { planSrid -> coordinateTransformationService.getTransformation(planSrid, LAYOUT_SRID) }
            ?.let { transformation -> getBoundingPolygonPointsFromAlignments(geometryPlan.alignments, transformation) }

        val planId = geometryService.getDuplicateGeometryPlanId(imFile)
        val duplicateFileName = planId?.let { plan -> geometryService.getPlanFile(plan).name.toString() } ?: ""
        if (duplicateFileName.length > 0) {
            throw InframodelParsingException(
                message = "InfraModel file exists already",
                localizedMessageKey = "$INFRAMODEL_PARSING_KEY_PARENT.duplicate-inframodel-file-content",
                localizedMessageParams = listOf(duplicateFileName),
            )
        }

        return geometryDao.insertPlan(geometryPlan, imFile, transformedBoundingBox)
    }

    fun validateInputFileAndParseInfraModel(file: MultipartFile, encodingOverride: String? = null): Pair<GeometryPlan, InfraModelFile> {
        logger.serviceCall(
            "validateInputFileAndParseGeometryPlan",
            "file.originalFilename" to file.originalFilename
        )
        checkForEmptyFileAndIncorrectFileType(file, MediaType.APPLICATION_XML, MediaType.TEXT_XML)
        val switchStructuresByType = switchLibraryService.getSwitchStructures().associateBy { it.type }
        val trackNumberIdsByNumber = trackNumberService.listOfficial().associate { tn -> tn.number to tn.id as IntId }

        return parseGeometryPlan(
            file,
            encodingOverride?.let(::findXmlCharset),
            geographyService.getCoordinateSystemNameToSridMapping(),
            switchStructuresByType,
            switchLibraryService.getInframodelAliases(),
            trackNumberIdsByNumber,
        )
    }

    fun validateInfraModelFile(
        file: MultipartFile,
        overrideParameters: OverrideParameters?
    ): ValidationResponse {
        logger.serviceCall(
            "validateInfraModelFile",
            "overrideParameters" to overrideParameters,
        )

        val parsedGeometryPlan = try {
            validateInputFileAndParseInfraModel(file, overrideParameters?.encoding).first
        } catch (e: Exception) {
            logger.warn("Failed to parse InfraModel", e)
            return ValidationResponse(
                validationErrors = listOf(ParsingError(
                    if (e is HasLocalizeMessageKey) e.localizedMessageKey
                    else LocalizationKey(INFRAMODEL_PARSING_KEY_GENERIC)
                )),
                geometryPlan = null,
                planLayout = null,
            )
        }

        return mapToTrackLayoutPlan(parsedGeometryPlan, overrideParameters)
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

        return mapToTrackLayoutPlan(geometryPlan, overrideParameters)
    }


    private fun mapToTrackLayoutPlan(
        plan: GeometryPlan,
        overrideParameters: OverrideParameters?,
    ): ValidationResponse {
        val planWithParameters = overrideGeometryPlanWithParameters(plan, overrideParameters, null)
        val (planLayout: GeometryPlanLayout?, layoutCreationError: TransformationError?) = geometryService.getTrackLayoutPlan(
            geometryPlan = planWithParameters,
            includeGeometryData = true,
            pointListStepLength = 10,
        )
        val validationErrors: List<ValidationError> =
             validateGeometryPlanContent(planWithParameters) + listOfNotNull(layoutCreationError)

        return ValidationResponse(validationErrors, planWithParameters, planLayout)
    }

    fun updateInfraModel(
        planId: IntId<GeometryPlan>,
        overrideParameters: OverrideParameters?,
        extraInfoParameters: ExtraInfoParameters?,
    ): GeometryPlan {
        logger.serviceCall(
            "updateInfraModel",
            "overrideParameters" to overrideParameters,
            "extraInfoParameters" to extraInfoParameters
        )

        val geometryPlan = geometryService.getGeometryPlan(planId)
        val overriddenPlan = overrideGeometryPlanWithParameters(geometryPlan, overrideParameters, extraInfoParameters)

        return geometryDao.updatePlan(planId, overriddenPlan)
    }

    private fun overrideGeometryPlanWithParameters(
        plan: GeometryPlan,
        overrideParameters: OverrideParameters?,
        extraInfoParameters: ExtraInfoParameters?,
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
        )
    }

    private fun validateGeometryPlanContent(geometryPlan: GeometryPlan): List<ValidationError> {
        return validate(
            geometryPlan,
            codeDictionaryService.getFeatureTypes(),
            switchLibraryService.getSwitchStructuresById(),
        )
    }
}
