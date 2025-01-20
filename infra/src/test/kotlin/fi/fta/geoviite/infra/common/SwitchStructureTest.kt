package fi.fta.geoviite.infra.common

import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchBaseType
import fi.fta.geoviite.infra.switchLibrary.SwitchHand
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType
import fi.fta.geoviite.infra.switchLibrary.SwitchTypeParts
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class SwitchStructureTest {
    private val knownSwitchTypes =
        listOf(
            "KRV43-233-1:9",
            "KRV43-270-1:9,514",
            "KRV54-200-1:9",
            "KRV54-200-1:9-O",
            "KV30-270-1:9,514",
            "KV30-270-1:9,514-V",
            "KV43-300-1:9,514",
            "KV43-300-1:9,514-O",
            "KV43-300-1:9,514-V",
            "KV54-200-1:9",
            "KV54-200-1:9-O",
            "KV54-200-1:9-V",
            "KV54-200N-1:9",
            "KV54-200N-1:9-O",
            "KV54-200N-1:9-V",
            "RR54-2x1:9",
            "RR54-4x1:9",
            "SKV60-1000/474-1:15,5-O",
            "SKV60-800/423-1:15,5-V",
            "SRR54-2x1:9-4,8",
            "SRR54-2x1:9-6,0",
            "SRR60-2x1:9-4.8",
            "TYV54-200-1:4,44",
            "TYV54-225-1:6,46",
            "TYV54-225-1:6,46TPE",
            "UKV54-1000/244-1:9-V",
            "UKV54-1500/228-1:9-O",
            "UKV54-800/258-1:9-V",
            "UKV60-600/281-1:9-O",
            "YRV54-200-1:9",
            "YV30-270-1:7-V",
            "YV30-270-1:9,514",
            "YV30-270-1:9,514-O",
            "YV30-270-1:9,514-V",
            "YV43-205-1:9",
            "YV43-205-1:9,514",
            "YV43-205-1:9,514-O",
            "YV43-205-1:9,514-V",
            "YV43-205-1:9-O",
            "YV43-205-1:9-V",
            "YV43-300-1:7",
            "YV43-300-1:7-O",
            "YV43-300-1:7-V",
            "YV43-300-1:9",
            "YV43-300-1:9,514",
            "YV43-300-1:9,514-O",
            "YV43-300-1:9,514-V",
            "YV43-300-1:9-O",
            "YV43-300-1:9-V",
            "YV43-530-1:15",
            "YV54-165-1:7",
            "YV54-165-1:7-O",
            "YV54-165-1:7-V",
            "YV54-200-1:9",
            "YV54-200-1:9-O",
            "YV54-200-1:9-V",
            "YV54-200N-1:9",
            "YV54-200N-1:9-O",
            "YV54-200N-1:9-V",
            "YV60-300-1:10",
            "YV60-300-1:10-O",
            "YV60-300-1:9",
            "YV60-300-1:9-O",
            "YV60-300-1:9-V",
            "YV60-300A-1:9-O",
            "YV60-300A-1:9-V",
            "YV60-300E-1:9-O",
            "YV60-300E-1:9-V",
            "YV60-300P-1:9",
            "YV60-300P-1:9-O",
            "YV60-300P-1:9-V",
            "YV60-5000/2500-1:26",
            "YV60-5000/2500-1:26-O",
            "YV60-5000/2500-1:26-V",
            "YV60-5000/3000-1:28-O",
            "YV60-5000/3000-1:28-V",
            "YV60-500-1:11,1",
            "YV60-500-1:11,1-O",
            "YV60-500-1:11,1-V",
            "YV60-500-1:14",
            "YV60-500-1:14-O",
            "YV60-500-1:14-V",
            "YV60-500A-1:14-V",
            "YV60-500E-1:14-O",
            "YV60-900-1:15,5",
            "YV60-900-1:15,5-O",
            "YV60-900-1:15,5-V",
            "YV60-900-1:18",
            "YV60-900-1:18-O",
            "YV60-900-1:18-V",
            "YV60-900A-1:15,5-O",
            "YV60-900A-1:18-O",
            "YV60-900A-1:18-V",
            "YV60-900P-1:18",
            "YV60-900P-1:18-O",
            "YV60-900P-1:18-V",

            // Non-Finnish types
            "EV-SJ43-5,9-1:9-H",
            "EV-SJ43-5,9-1:9-V",
        )

    private val validSwitchStructure by lazy {
        SwitchStructureData(
            type = SwitchType("YV60-300-1:9-O"),
            presentationJointNumber = JointNumber(1),
            joints =
                setOf(
                    SwitchStructureJoint(number = JointNumber(1), location = Point(0.0, 0.0)),
                    SwitchStructureJoint(number = JointNumber(5), location = Point(16.615, 0.0)),
                    SwitchStructureJoint(number = JointNumber(2), location = Point(34.430, 0.0)),
                    SwitchStructureJoint(number = JointNumber(3), location = Point(34.430, 3.825)),
                ),
            alignments =
                listOf(
                    SwitchStructureAlignment(
                        jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                        elements =
                            listOf(
                                SwitchStructureLine(start = Point(0.0, 0.0), end = Point(16.615, 0.0)),
                                SwitchStructureLine(start = Point(16.615, 0.0), end = Point(34.430, 0.0)),
                            ),
                    ),
                    SwitchStructureAlignment(
                        jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                        elements =
                            listOf(
                                SwitchStructureCurve(start = Point(0.0, 0.0), end = Point(34.0, 3.777), radius = 300.0),
                                SwitchStructureLine(start = Point(34.0, 3.5), end = Point(34.430, 3.825)),
                            ),
                    ),
                ),
        )
    }

    private val nonExistingJointNumber = JointNumber(999)

    @Test
    fun switchTypeParsingAllowsKnownTypes() {
        knownSwitchTypes.forEach { type -> assertDoesNotThrow { SwitchType(type) } }
    }

    @Test
    fun switchTypeParsingFindsExpectedParts() {
        assertEquals(
            SwitchTypeParts(SwitchBaseType.YV, 60, listOf(900), "P", "1:18", SwitchHand.RIGHT),
            SwitchType("YV60-900P-1:18-O").parts,
        )
        assertEquals(
            SwitchTypeParts(SwitchBaseType.KRV, 43, listOf(270), null, "1:9,514", SwitchHand.NONE),
            SwitchType("KRV43-270-1:9,514").parts,
        )
        assertEquals(
            SwitchTypeParts(SwitchBaseType.YV, 60, listOf(5000, 2500), null, "1:26", SwitchHand.LEFT),
            SwitchType("YV60-5000/2500-1:26-V").parts,
        )
        assertEquals(
            SwitchTypeParts(SwitchBaseType.SRR, 54, listOf(), null, "2x1:9-6,0", SwitchHand.NONE),
            SwitchType("SRR54-2x1:9-6,0").parts,
        )
        assertEquals(
            SwitchTypeParts(SwitchBaseType.EV, 43, listOf(), null, "1:9", SwitchHand.RIGHT),
            SwitchType("EV-SJ43-5,9-1:9-H").parts,
        )
        assertEquals(
            SwitchTypeParts(SwitchBaseType.EV, 43, listOf(), null, "1:9", SwitchHand.LEFT),
            SwitchType("EV-SJ43-5,9-1:9-V").parts,
        )
    }

    @Test
    fun switchTypeDeniesInvalidValues() {
        assertThrows<IllegalArgumentException> { SwitchType("foo") }
    }

    @Test
    fun switchStructureAcceptsValidValues() {
        validSwitchStructure
    }

    @Test
    fun switchStructureDeniesInvalidPresentationJointNumber() {
        assertThrows<IllegalArgumentException> {
            SwitchStructureData(
                validSwitchStructure.type,
                presentationJointNumber = nonExistingJointNumber,
                validSwitchStructure.joints,
                validSwitchStructure.alignments,
            )
        }
    }

    @Test
    fun switchStructureDeniesEmptyAlignments() {
        assertThrows<IllegalArgumentException> {
            SwitchStructureData(
                validSwitchStructure.type,
                validSwitchStructure.presentationJointNumber,
                validSwitchStructure.joints,
                listOf(),
            )
        }
    }

    @Test
    fun switchAlignmentDeniesEmptyJoints() {
        assertThrows<IllegalArgumentException> {
            SwitchStructureAlignment(jointNumbers = listOf(), validSwitchStructure.alignments[0].elements)
        }
    }

    @Test
    fun switchAlignmentDeniesEmptyElements() {
        assertThrows<IllegalArgumentException> {
            SwitchStructureAlignment(validSwitchStructure.alignments[0].jointNumbers, elements = listOf())
        }
    }

    @Test
    fun switchStructureDeniesInvalidAlignmentJointNumber() {
        assertThrows<IllegalArgumentException> {
            SwitchStructureData(
                validSwitchStructure.type,
                validSwitchStructure.presentationJointNumber,
                validSwitchStructure.joints,
                validSwitchStructure.alignments.map {
                    it.copy(
                        // make alignment joint numbers invalid
                        jointNumbers = listOf(nonExistingJointNumber)
                    )
                },
            )
        }
    }

    @Test
    fun flipAlongYAxisProducesValidValues() {
        val flipped = validSwitchStructure.flipAlongYAxis()
        validSwitchStructure.joints.forEach { joint ->
            assertEquals(joint.location.x, flipped.getJoint(joint.number).location.x)
            assertEquals(joint.location.y, -flipped.getJoint(joint.number).location.y)
        }
        validSwitchStructure.alignments.forEachIndexed { alignmentIndex, alignment ->
            alignment.elements.forEachIndexed { elementIndex, switchElement ->
                assertEquals(switchElement.start.x, flipped.alignments[alignmentIndex].elements[elementIndex].start.x)
                assertEquals(switchElement.start.y, -flipped.alignments[alignmentIndex].elements[elementIndex].start.y)
                assertEquals(switchElement.end.x, flipped.alignments[alignmentIndex].elements[elementIndex].end.x)
                assertEquals(switchElement.end.y, -flipped.alignments[alignmentIndex].elements[elementIndex].end.y)
            }
        }
    }

    @Test
    fun allBaseTypesAreValidAsSwitchName() {
        // For suggestions without name, we generate a temp-name from the basetype. Ensure that
        // works
        SwitchBaseType.entries.forEach { type -> assertDoesNotThrow { SwitchName(type.name) } }
    }
}
