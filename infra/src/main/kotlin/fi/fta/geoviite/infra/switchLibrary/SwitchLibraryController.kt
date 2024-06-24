package fi.fta.geoviite.infra.switchLibrary

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping

@GeoviiteController("/switch-library")
class SwitchLibraryController(private val switchLibraryService: SwitchLibraryService) {

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @GetMapping("/switch-structures")
    fun getSwitchStructures(): List<SwitchStructure> {
        return switchLibraryService.getSwitchStructures()
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @GetMapping("/switch-owners")
    fun getSwitchOwners(): List<SwitchOwner> {
        return switchLibraryService.getSwitchOwners()
    }
}
