package fi.fta.geoviite.infra.projektivelho

import java.net.URI
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/redirect/projektivelho")
class PVRedirectController(@Value("\${geoviite.projektivelho.ui_domain}") private val velhoUiUrl: String) {

    @GetMapping("/files")
    fun redirectToVelho(
        @RequestParam projectGroup: String? = null,
        @RequestParam project: String? = null,
        @RequestParam assignment: String? = null,
        @RequestParam document: String? = null,
    ): ResponseEntity<Void> {
        val path =
            when {
                project != null -> {
                    val assignmentSegment = if (assignment != null) "/toimeksiannot/oid-$assignment" else ""
                    val documentSegment =
                        if (assignment != null && document != null) "/aineistot/oid-$document/muokkaa" else ""
                    "/projektivelho/projektit/oid-$project$assignmentSegment$documentSegment"
                }
                projectGroup != null -> "/projektivelho/projektijoukot/oid-$projectGroup"
                else -> "/projektivelho"
            }
        val headers = HttpHeaders()
        headers.location = URI.create("$velhoUiUrl$path")
        return ResponseEntity(headers, HttpStatus.FOUND)
    }
}
