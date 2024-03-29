package fi.fta.geoviite.infra.switchLibrary

import fi.fta.geoviite.infra.authorization.AUTH_VIEW_GEOMETRY
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT
import fi.fta.geoviite.infra.logging.apiCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/switch-library")
class SwitchLibraryController(private val switchLibraryService: SwitchLibraryService) {

    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @GetMapping("/switch-structures")
    fun getSwitchStructures(): List<SwitchStructure> {
        log.apiCall("getSwitchStructures")
        return switchLibraryService.getSwitchStructures()
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @GetMapping("/switch-owners")
    fun getSwitchOwners(): List<SwitchOwner> {
        log.apiCall("getSwitchOwners")
        return switchLibraryService.getSwitchOwners()
    }
}
