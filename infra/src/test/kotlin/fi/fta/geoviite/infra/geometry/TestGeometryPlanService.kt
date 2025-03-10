package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geography.boundingPolygonPointsByConvexHull
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.rotateAroundOrigin
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.ui.testdata.createGeometryAlignment
import fi.fta.geoviite.infra.ui.testdata.createGeometryKmPost
import fi.fta.geoviite.infra.ui.testdata.tmi35GeometryUnit
import fi.fta.geoviite.infra.ui.testgroup2.DEFAULT_BASE_POINT
import fi.fta.geoviite.infra.ui.testgroup2.LINKING_TEST_PLAN_NAME
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile

@Profile("test")
@GeoviiteService
class TestGeometryPlanService
@Autowired
constructor(val switchStructureDao: SwitchStructureDao, val geometryDao: GeometryDao) {
    class BuildGeometryAlignment(val name: String, val firstPoint: Point, val incrementPoints: List<Point>) {
        val switchData: MutableList<SwitchData> = mutableListOf()
    }

    inner class BuildGeometryPlan(val trackNumber: TrackNumber) {
        val alignments: MutableList<BuildGeometryAlignment> = mutableListOf()
        val kmPosts: MutableList<GeometryKmPost> = mutableListOf()
        val switches: MutableList<GeometrySwitch> = mutableListOf()

        fun alignment(name: String, firstPoint: Point, vararg incrementPoints: Point): BuildGeometryPlan {
            alignments.add(BuildGeometryAlignment(name, firstPoint, incrementPoints.asList()))
            return this
        }

        fun switchData(switchName: String, startJointNumber: Int?, endJointNumber: Int?): BuildGeometryPlan {
            alignments
                .last()
                .switchData
                .add(
                    SwitchData(
                        StringId(switchName),
                        startJointNumber?.let(::JointNumber),
                        endJointNumber?.let(::JointNumber),
                    )
                )
            return this
        }

        fun kmPost(name: String, location: Point): BuildGeometryPlan {
            kmPosts.add(createGeometryKmPost(DEFAULT_BASE_POINT + location, name))
            return this
        }

        fun switch(name: String, typeName: String, location: Point, rotationRad: Double = 0.0): BuildGeometryPlan {
            val switchStructure =
                switchStructureDao.fetchSwitchStructures().first { structure -> structure.type.typeName == typeName }

            switches.add(
                GeometrySwitch(
                    id = StringId(name),
                    name = SwitchName(name),
                    typeName = GeometrySwitchTypeName(typeName),
                    switchStructureId = switchStructure.id,
                    state = PlanState.EXISTING,
                    joints =
                        switchStructure.joints.map { ssj ->
                            GeometrySwitchJoint(
                                ssj.number,
                                DEFAULT_BASE_POINT + location + rotateAroundOrigin(rotationRad, ssj.location),
                            )
                        },
                )
            )
            return this
        }

        fun save(): GeometryPlan {
            val builtAlignments =
                alignments.map { build ->
                    createGeometryAlignment(
                        alignmentName = build.name,
                        basePoint = DEFAULT_BASE_POINT + build.firstPoint,
                        incrementPoints = build.incrementPoints,
                        switchData = build.switchData,
                    )
                }
            return saveAndRefetchGeometryPlan(
                plan(
                    trackNumber,
                    alignments = builtAlignments,
                    kmPosts = kmPosts,
                    switches = switches,
                    project = project(LINKING_TEST_PLAN_NAME),
                    srid = LAYOUT_SRID,
                    units = tmi35GeometryUnit(),
                ),
                boundingPolygonPointsByConvexHull(
                    builtAlignments.flatMap { alignment -> alignment.elements.flatMap { element -> element.bounds } } +
                        kmPosts.mapNotNull { kmPost -> kmPost.location },
                    LAYOUT_SRID,
                ),
            )
        }
    }

    fun buildPlan(trackNumber: TrackNumber) = BuildGeometryPlan(trackNumber)

    fun saveAndRefetchGeometryPlan(plan: GeometryPlan, boundingBox: List<Point>): GeometryPlan {
        return geometryDao.fetchPlan(geometryDao.insertPlan(plan, testFile(), boundingBox))
    }
}
