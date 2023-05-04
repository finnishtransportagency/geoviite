package fi.fta.geoviite.infra.velho

import VelhoCode
import VelhoDocument
import VelhoDocumentHeader
import VelhoDocumentStatus
import VelhoDocumentStatus.PENDING
import VelhoEncoding
import VelhoName
import VelhoProject
import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.inframodel.ExtraInfoParameters
import fi.fta.geoviite.infra.inframodel.OverrideParameters
import fi.fta.geoviite.infra.inframodel.ValidationResponse
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.Instant

val dummyData = listOf(
    VelhoDocumentHeader(
        id = IntId(1),
        project = VelhoProject(
            name = VelhoName("test project 1"),
            group = VelhoName("test group A"),
        ),
        assignment = VelhoName("Rakentamissuunnittelu"),
        materialGroup = VelhoName("Suunnitelma / suunnitelmakokonaisuus"),
        document = VelhoDocument(
            name = FileName("test1.xml"),
            description = FreeText("asdf/zxcv/test1"),
            type = VelhoEncoding(
                VelhoCode("documenttype/123"),
                VelhoName("Suunnitelmamalli"),
            ),
            modified = Instant.now().minusSeconds(60*60*3),
            status = PENDING,
        ),
    ),

    VelhoDocumentHeader(
        id = IntId(2),
        project = VelhoProject(
            name = VelhoName("test project 1"),
            group = VelhoName("test group A"),
        ),
        assignment = VelhoName("Rakentamissuunnittelu"),
        materialGroup = VelhoName("Suunnitelma / suunnitelmakokonaisuus"),
        document = VelhoDocument(
            name = FileName("test2.xml"),
            description = FreeText("asdf/zxcv/test2"),
            type = VelhoEncoding(
                VelhoCode("documenttype/123"),
                VelhoName("Suunnitelmamalli"),
            ),
            modified = Instant.now().minusSeconds(60*60*2),
            status = PENDING,
        ),
    ),

    VelhoDocumentHeader(
        id = IntId(3),
        project = VelhoProject(
            name = VelhoName("test project 2"),
            group = VelhoName("test group B"),
        ),
        assignment = VelhoName("Rakentamissuunnittelu"),
        materialGroup = VelhoName("Suunnitelma / suunnitelmakokonaisuus"),
        document = VelhoDocument(
            name = FileName("test3.xml"),
            description = FreeText("asdf/zxcv/test3"),
            type = VelhoEncoding(
                VelhoCode("documenttype/123"),
                VelhoName("Suunnitelmamalli"),
            ),
            modified = Instant.now().minusSeconds(60*60),
            status = PENDING,
        ),
    )
)

@RestController
@RequestMapping("/velho")
class VelhoController {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/document-headers")
    fun getDocumentHeaders(@RequestParam("status") status: VelhoDocumentStatus?): List<VelhoDocumentHeader> {
        logger.apiCall("getDocumentHeaders", "status" to status)
        return dummyData
    }

    @PreAuthorize(AUTH_ALL_READ)
    @PostMapping("/validate/{id}")
    fun validateVelhoFile(
        @RequestPart(value = "override-parameters") overrideParameters: OverrideParameters?,
    ): ValidationResponse {
        logger.apiCall("override-parameters", "overrideParameters" to overrideParameters)
        TODO("Not implemented yet") // GVT-1797
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/import/{id}")
    fun importVelhoFile(
        @RequestPart(value = "override-parameters") overrideParameters: OverrideParameters?,
        @RequestPart(value = "extrainfo-parameters") extraInfoParameters: ExtraInfoParameters?,
    ): IntId<GeometryPlan> {
        logger.apiCall("importVelhoFile",
            "overrideParameters" to overrideParameters,
            "extraInfoParameters" to extraInfoParameters,
        )
        TODO("Not implemented yet") // GVT-1797
    }
}