package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.tracklayout.MapAlignment
import fi.fta.geoviite.infra.tracklayout.TrackLayoutKmPost
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.simplify
import fi.fta.geoviite.infra.util.FileName

data class GeometryPlanLayout(
    val fileName: FileName,
    val alignments: List<MapAlignment<GeometryAlignment>>,
    val switches: List<TrackLayoutSwitch>,
    val kmPosts: List<TrackLayoutKmPost>,
    val boundingBox: BoundingBox? = boundingBoxCombining(alignments.mapNotNull { a -> a.boundingBox }),
    val planId: DomainId<GeometryPlan>,
    val planDataType: DataType,
)

fun simplifyPlanLayout(layout: GeometryPlanLayout, resolution: Int) = layout.copy(
    alignments = layout.alignments.map { mapAlignment -> simplify(mapAlignment, resolution) }
)
