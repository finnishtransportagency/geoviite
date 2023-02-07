package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.tracklayout.GeometryPlanLayout
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.toFileDownloadResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.time.Instant

data class ValidationResponse(
    val validationErrors: List<ValidationError>,
    val geometryPlan: GeometryPlan?,
    val planLayout: GeometryPlanLayout?,
)

data class InsertResponse(
    val message: String,
    val planId: IntId<GeometryPlan>,
)

data class ExtraInfoParameters(
    val oid: Oid<GeometryPlan>?,
    val planPhase: PlanPhase?,
    val decisionPhase: PlanDecisionPhase?,
    val measurementMethod: MeasurementMethod?,
    val message: FreeText?,
)

data class OverrideParameters(
    val coordinateSystemSrid: Srid?,
    val verticalCoordinateSystem: VerticalCoordinateSystem?,
    val projectId: IntId<Project>?,
    val authorId: IntId<Author>?,
    val trackNumberId: IntId<TrackLayoutTrackNumber>?,
    val createdDate: Instant?,
    val encoding: String?
)

@RestController
@RequestMapping("/inframodel")
class InfraModelController @Autowired constructor(
    private val infraModelService: InfraModelService,
    private val geometryService: GeometryService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping()
    fun saveInfraModel(
        @RequestPart(value = "file") file: MultipartFile,
        @RequestPart(value = "override-parameters") overrideParameters: OverrideParameters?,
        @RequestPart(value = "extrainfo-parameters") extraInfoParameters: ExtraInfoParameters?,
    ): InsertResponse {
        logger.apiCall(
            "saveInfraModel",
            "file.originalFilename" to file.originalFilename,
            "coordinateSystemSrid" to overrideParameters?.coordinateSystemSrid,
            "verticalCoordinateSystem" to overrideParameters?.verticalCoordinateSystem,
            "oid" to extraInfoParameters?.oid,
            "planPhase" to extraInfoParameters?.planPhase,
            "decisionPhase" to extraInfoParameters?.decisionPhase,
            "measurementMethod" to extraInfoParameters?.measurementMethod,
            "message" to extraInfoParameters?.message,
            "createdDate" to overrideParameters?.createdDate,
            "projectId" to overrideParameters?.projectId,
            "authorId" to overrideParameters?.authorId,
            "trackNumberId" to overrideParameters?.trackNumberId,
            "encodingOverride" to overrideParameters?.encoding
        )
        return InsertResponse("New plan inserted successfully",
            infraModelService.saveInfraModel(file, overrideParameters, extraInfoParameters).id)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @PostMapping("/validate")
    fun validateFile(
        @RequestPart(value = "file") file: MultipartFile,
        @RequestPart(value = "override-parameters") overrideParameters: OverrideParameters?,
    ): ValidationResponse {
        logger.apiCall(
            "validateFile",
            "file.originalFilename" to file.originalFilename,
            "coordinateSystemSrid" to overrideParameters?.coordinateSystemSrid,
            "verticalCoordinateSystem" to overrideParameters?.verticalCoordinateSystem,
            "projectId" to overrideParameters?.projectId,
            "authorId" to overrideParameters?.authorId,
            "trackNumber" to overrideParameters?.trackNumberId,
            "createdDate" to overrideParameters?.createdDate,
            "encodingOverride" to overrideParameters?.encoding
        )
        return infraModelService.validateInfraModelFile(file, overrideParameters)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @PostMapping("/{planId}/validate")
    fun validateGeometryPlan(
        @PathVariable("planId") planId: IntId<GeometryPlan>,
        @RequestPart(value = "override-parameters") overrideParameters: OverrideParameters?,
    ): ValidationResponse? {
        logger.apiCall(
            "validateGeometryPlan",
            "planId" to planId,
            "overrideParameters" to overrideParameters
        )

        return infraModelService.validateGeometryPlan(planId, overrideParameters)
    }


    @PreAuthorize(AUTH_ALL_WRITE)
    @PutMapping("/{planId}")
    fun updateInfraModel(
        @PathVariable("planId") planId: IntId<GeometryPlan>,
        @RequestPart(value = "override-parameters") overrideParameters: OverrideParameters?,
        @RequestPart(value = "extrainfo-parameters") extraInfoParameters: ExtraInfoParameters?,
    ): GeometryPlan {
        logger.apiCall(
            "updateInfraModel",
            "overrideParameters" to overrideParameters,
            "extraInfoParameters" to extraInfoParameters)

        return infraModelService.updateInfraModel(planId, overrideParameters, extraInfoParameters)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("{id}/file", MediaType.APPLICATION_OCTET_STREAM_VALUE)
    fun downloadFile(@PathVariable("id") id: IntId<GeometryPlan>): ResponseEntity<ByteArray> {
        logger.apiCall("downloadFile", "id" to id)
        val infraModelFile: InfraModelFile = geometryService.getPlanFile(id)
        return toFileDownloadResponse(fileNameWithSuffix(infraModelFile.name), infraModelFile.content.toByteArray())
    }
}

private fun fileNameWithSuffix(fileName: FileName): String =
    if (fileName.endsWith(".xml", true)) fileName.toString()
    else "$fileName.xml"
