package fi.fta.geoviite.infra.switchLibrary

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.geometry.MetaDataName

data class SwitchOwner(val id: IntId<SwitchOwner>, val name: MetaDataName)
