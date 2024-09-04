package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.RotationDirection.CCW
import fi.fta.geoviite.infra.common.RotationDirection.CW
import fi.fta.geoviite.infra.geometry.CantRotationPoint.INSIDE_RAIL
import fi.fta.geoviite.infra.geometry.CantTransitionType.LINEAR
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.math.rotateAroundOrigin
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import kotlin.math.PI
import kotlin.math.hypot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private const val JOINT_LOCATION_DELTA = 0.01

class ValidationTest {

    private val yvStructure = switchStructureYV60_300_1_9()

    @Test
    fun validationFindsNothingInValidGeometry() {
        val alignment = geometryAlignment(elements = lines(Point(1.0, 2.0), 4, 12.34))
        assertEquals(listOf<GeometryValidationIssue>(), validateAlignmentGeometry(alignment))
    }

    @Test
    fun validationFindsIncorrectLineLength() {
        val alignment = geometryAlignment(elements = lines(Point(1.0, 2.0), 1, 12.34, 1.0))
        assertGeometryValidationIssues(
            validateAlignmentGeometry(alignment),
            listOf("$VALIDATION_ELEMENT.field-incorrect-length"),
        )
    }

    @Test
    fun validationFindsNonContinuousLine() {
        val lines = lines(Point(1.0, 2.0), 3, 12.34).filterIndexed { i, _ -> i % 2 == 0 }
        assertGeometryValidationIssues(
            validateAlignmentGeometry(geometryAlignment(elements = lines)),
            listOf("$VALIDATION_ELEMENT.coordinates-not-continuous"),
        )
    }

    @Test
    fun validationFindsMissingCant() {
        val alignment = geometryAlignment(elements = lines(Point(1.0, 2.0), 1, 12.34, 1.0))
        assertGeometryValidationIssues(validateAlignmentCant(alignment), listOf("$VALIDATION_ALIGNMENT.no-cant"))
    }

    @Test
    fun validationFindsMissingProfile() {
        val alignment = geometryAlignment(elements = lines(Point(1.0, 2.0), 1, 12.34, 1.0), profile = null)
        assertGeometryValidationIssues(validateAlignmentProfile(alignment), listOf("$VALIDATION_ALIGNMENT.no-profile"))
    }

    @Test
    fun validationFindsNothingInValidProfile() {
        val points = listOf(viPoint(0.0, 2.0), viPoint(10.1, 1.0))
        val alignment = geometryAlignment(profile = profile(points))
        assertEquals(listOf<GeometryValidationIssue>(), validateAlignmentProfile(alignment))
    }

    @Test
    fun validationFindsProfileStationNonContinuous() {
        val points = listOf(viPoint(1.0, 0.02), viPoint(0.1, 0.01), viPoint(3.0, 0.04))
        val alignment = geometryAlignment(profile = profile(points))
        assertGeometryValidationIssues(
            validateAlignmentProfile(alignment),
            listOf("$VALIDATION_PROFILE.incorrect-station", "$VALIDATION_PROFILE.calculation-failed"),
        )
    }

    @Test
    fun validationFindsProfileToSteep() {
        val points = listOf(viPoint(1.0, 1.0), viPoint(2.0, 2.0))
        val alignment = geometryAlignment(profile = profile(points))
        assertGeometryValidationIssues(
            validateAlignmentProfile(alignment),
            listOf("$VALIDATION_PROFILE.incorrect-slope"),
        )
    }

    @Test
    fun validationFindsNothingInValidCant() {
        val points = listOf(cantPoint(0.0, 0.2, CW), cantPoint(1.1, 0.1, CW), cantPoint(2.1, 0.15, CCW))
        val alignment = geometryAlignment(cant = cant(points))
        assertEquals(listOf<GeometryValidationIssue>(), validateAlignmentCant(alignment))
    }

    @Test
    fun validationFindsCantStationNonContinuous() {
        val points = listOf(cantPoint(1.0, 0.1, CCW), cantPoint(0.5, 0.2, CCW), cantPoint(3.0, 0.3, CCW))
        val alignment = geometryAlignment(cant = cant(points))
        assertGeometryValidationIssues(
            validateAlignmentCant(alignment),
            listOf("$VALIDATION_CANT.station-not-continuous"),
        )
    }

    @Test
    fun `validation finds cant missing from track`() {
        val alignment =
            geometryAlignment(
                cant =
                    cant(
                        points = listOf(cantPoint(0.0, 0.2, CW), cantPoint(1.1, 0.1, CW), cantPoint(2.1, 0.15, CCW)),
                        rotationPoint = null,
                    ),
                featureTypeCode = FeatureTypeCode("281"), // cant should exist on tracks
            )

        assertGeometryValidationIssues(
            validateAlignmentCant(alignment),
            listOf("$VALIDATION_ALIGNMENT.cant-rotation-point-undefined"),
        )
    }

    @Test
    fun validationFindsCantOverGauge() {
        val points = listOf(cantPoint(1.0, 0.1, CCW), cantPoint(2.0, 2.0, CCW), cantPoint(3.0, 0.3, CCW))
        val alignment = geometryAlignment(cant = cant(points))
        assertGeometryValidationIssues(validateAlignmentCant(alignment), listOf("$VALIDATION_CANT.value-incorrect"))
    }

    @Test
    fun validationFindsNothingInValidSwitch() {
        val location = Point(100.0, 150.0)
        val angle = PI / 4
        val switch = createSwitch(yvStructure, location, angle)
        val alignments = createSwitchAlignments(switch, yvStructure)
        val alignmentSwitches =
            alignments.mapNotNull { alignment -> collectAlignmentSwitchJoints(switch.id, alignment) }
        assertEquals(listOf<GeometryValidationIssue>(), validateSwitch(switch, yvStructure, alignmentSwitches))
    }

    @Test
    fun validationNoticesSwitchJointPointsNotMeeting() {
        val validSwitch = createSwitch(yvStructure, Point(100.0, 150.0), PI / 4)
        val invalidSwitch =
            validSwitch.copy(
                joints =
                    validSwitch.joints.map { joint ->
                        // Move first joint a bit off
                        if (joint.number == JointNumber(1)) {
                            joint.copy(
                                location = joint.location.copy(x = joint.location.x + JOINT_LOCATION_DELTA * 1.1)
                            )
                        } else {
                            joint
                        }
                    }
            )
        val alignments = createSwitchAlignments(invalidSwitch, yvStructure)
        val alignmentSwitches =
            alignments.mapNotNull { alignment -> collectAlignmentSwitchJoints(invalidSwitch.id, alignment) }
        assertGeometryValidationIssues(
            validateSwitch(invalidSwitch, yvStructure, alignmentSwitches),
            listOf("$VALIDATION_SWITCH.incorrect-joint-locations"),
        )
    }

    @Test
    fun validationNoticesSwitchJointPointsInaccuracy() {
        val validSwitch = createSwitch(yvStructure, Point(100.0, 150.0), PI / 4)
        val inaccurateSwitch =
            validSwitch.copy(
                joints =
                    validSwitch.joints.map { joint ->
                        // Move first joint a bit off
                        if (joint.number == JointNumber(1)) {
                            joint.copy(
                                location = joint.location.copy(x = joint.location.x + JOINT_LOCATION_DELTA * 0.9)
                            )
                        } else {
                            joint
                        }
                    }
            )
        val alignments = createSwitchAlignments(inaccurateSwitch, yvStructure)
        val alignmentSwitches =
            alignments.mapNotNull { alignment -> collectAlignmentSwitchJoints(inaccurateSwitch.id, alignment) }
        assertGeometryValidationIssues(
            validateSwitch(inaccurateSwitch, yvStructure, alignmentSwitches),
            listOf("$VALIDATION_SWITCH.inaccurate-joint-locations"),
        )
    }

    private fun assertGeometryValidationIssues(errors: List<GeometryValidationIssue>, keyParts: List<String>) {
        assertEquals(keyParts.size, errors.size, errors.toString())
        errors.forEachIndexed { index, error ->
            assertEquals(LocalizationKey("$VALIDATION.${keyParts[index]}"), error.localizationKey, errors.toString())
        }
    }

    private fun profile(intersections: List<VerticalIntersection>): GeometryProfile {
        return GeometryProfile(PlanElementName("Test Profile"), intersections)
    }

    private fun viPoint(station: Double, height: Double): VIPoint {
        return VIPoint(PlanElementName("Test Point"), Point(station, height))
    }

    private fun cant(points: List<GeometryCantPoint>, rotationPoint: CantRotationPoint? = INSIDE_RAIL): GeometryCant {
        return GeometryCant(
            PlanElementName("TC001"),
            PlanElementName("Test Cant"),
            FINNISH_RAIL_GAUGE,
            rotationPoint,
            points,
        )
    }

    private fun cantPoint(station: Double, appliedCant: Double, curvature: RotationDirection): GeometryCantPoint {
        return GeometryCantPoint(station.toBigDecimal(), appliedCant.toBigDecimal(), curvature, LINEAR)
    }

    private fun createSwitch(structure: SwitchStructure, locationOffset: Point, angle: Double) =
        GeometrySwitch(
            name = SwitchName("T001"),
            switchStructureId = structure.id as IntId,
            typeName = GeometrySwitchTypeName(structure.type.typeName),
            state = PlanState.PROPOSED,
            joints =
                listOf(
                    geometrySwitchJoint(1, structure, locationOffset, angle),
                    geometrySwitchJoint(5, structure, locationOffset, angle),
                    geometrySwitchJoint(2, structure, locationOffset, angle),
                    geometrySwitchJoint(3, structure, locationOffset, angle),
                ),
        )

    private fun createSwitchAlignments(switch: GeometrySwitch, structure: SwitchStructure) =
        structure.alignments.map { structureAlignment ->
            geometryAlignment(
                elements =
                    structureAlignment.jointNumbers.mapIndexedNotNull { index, jointNumber ->
                        structureAlignment.jointNumbers.getOrNull(index + 1)?.let { nextJointNumber ->
                            val startPoint = switch.getJoint(jointNumber)!!.location
                            val endPoint = switch.getJoint(nextJointNumber)!!.location
                            line(
                                start = startPoint,
                                end = endPoint,
                                name = switch.name.toString(),
                                switchData =
                                    SwitchData(
                                        switchId = switch.id,
                                        startJointNumber = jointNumber,
                                        endJointNumber = null,
                                    ),
                            )
                        }
                    }
            )
        }

    private fun geometrySwitchJoint(number: Int, structure: SwitchStructure, locationOffset: Point, angle: Double) =
        JointNumber(number).let { jn ->
            GeometrySwitchJoint(jn, getTransformedPoint(jn, structure, locationOffset, angle))
        }

    private fun getTransformedPoint(
        number: JointNumber,
        structure: SwitchStructure,
        locationOffset: Point,
        angle: Double,
    ): Point = rotateAroundOrigin(angle, structure.getJointLocation(number)) + locationOffset

    private fun lines(
        startPoint: Point,
        segmentCount: Int,
        segmentOffset: Double,
        lengthError: Double = 0.0,
    ): List<GeometryElement> {
        return (0 until segmentCount).map { i ->
            val start = startPoint + Point(i * segmentOffset, i * segmentOffset)
            val end = startPoint + Point((i + 1) * segmentOffset, (i + 1) * segmentOffset)
            val staStart = hypot(i * segmentOffset, i * segmentOffset) + i * lengthError
            val length = lineLength(start, end) + lengthError
            line(start = start, end = end, staStart = staStart, length = length)
        }
    }
}
