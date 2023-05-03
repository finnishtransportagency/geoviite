package fi.fta.geoviite.infra.velho.projektivelho

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.util.toResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/projektivelho")
@ConditionalOnBean(ProjektiVelhoClientConfiguration::class)
class ProjektiVelhoController(private val projektiVelhoService: ProjektiVelhoService) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/ookkonää")
    fun fetchFilesFromVelho(): ResponseEntity<Any> {
        logger.apiCall("fetchFilesFromVelho")
        return toResponse(projektiVelhoService.fetch())
    }
}
