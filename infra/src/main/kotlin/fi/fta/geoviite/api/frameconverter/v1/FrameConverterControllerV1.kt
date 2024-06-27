package fi.fta.geoviite.api.frameconverter.v1

import fi.fta.geoviite.api.aspects.GeoviiteIntegrationApiController
import org.springframework.web.bind.annotation.GetMapping

@GeoviiteIntegrationApiController("/frame-converter/v1")
class FrameConverterControllerV1 {

    @GetMapping("/some-conversion")
    fun someConversion(): String {
        return "some response"
    }
}
