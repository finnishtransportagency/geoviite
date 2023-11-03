package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.geometry.MetaDataName

data class LocationTrackOwner(val id: IntId<LocationTrackOwner>, val name: MetaDataName)
