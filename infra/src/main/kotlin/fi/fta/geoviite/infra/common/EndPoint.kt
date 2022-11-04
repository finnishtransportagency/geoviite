package fi.fta.geoviite.infra.common

import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch


enum class EndPointType {
    SWITCH, // vaihde
    LOCATION_TRACK, // jatkuu toisena raiteena
    ENDPOINT // raide päättyy
    /*
    KAANTOPOYTA,
    ASEMA,
    KISKON_PAA,
    OMISTUSRAJA,
    LIIKENNOINNIN_RAJA,
    VAIHDEPIIRIN_RAJA,
    LIIKENNEPAIKAN_RAJA,
    VALTAKUNNAN_RAJA,
    HAAPARANTA,
     */
}

abstract class EndPoint(
    open val type: EndPointType,
)

data class EndPointSimple(
    override val type: EndPointType,
) : EndPoint(type)

data class EndPointSwitch(
    val switchId: IntId<TrackLayoutSwitch>,
) : EndPoint(EndPointType.SWITCH)

data class EndPointLocationTrack(
    val locationTrackId: IntId<LocationTrack>,
) : EndPoint(EndPointType.LOCATION_TRACK)
