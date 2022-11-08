package fi.fta.geoviite.infra.inframodel

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.codeDictionary.CodeDictionaryService
import fi.fta.geoviite.infra.codeDictionary.FeatureType
import fi.fta.geoviite.infra.common.FeatureTypeCode
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.parseKmNumber
import fi.fta.geoviite.infra.geography.*
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.assertApproximatelyEquals
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.LAYOUT_CRS
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.FileName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.io.File
import java.io.StringWriter
import java.math.BigDecimal

const val TESTFILE_SIMPLE = "/inframodel/testfile_simple.xml"
const val TESTFILE_CLOTHOID_AND_PARABOLA = "/inframodel/testfile_clothoid_and_parabola.xml"

@ActiveProfiles("dev", "test")
@SpringBootTest
class InfraModelParsingIT @Autowired constructor(
    codeDictionaryService: CodeDictionaryService,
    geographyService: GeographyService,
    val heightTriangleDao: HeightTriangleDao,
    switchStructureDao: SwitchStructureDao,
    val trackNumberDao: LayoutTrackNumberDao,
    val kkJtoETRSTriangulationDao: KKJtoETRSTriangulationDao,
): ITTestBase() {
    private val mapper = jacksonObjectMapper()
    private val featureTypes: List<FeatureType> = codeDictionaryService.getFeatureTypes()
    private val coordinateSystemNameToSrid = geographyService.getCoordinateSystemNameToSridMapping()
    private val switchStructuresByType = switchStructureDao.fetchSwitchStructures().associateBy { it.type }
    private val switchStructuresById = switchStructureDao.fetchSwitchStructures().associateBy { it.id as IntId }
    private val switchTypeNameAliases = switchStructureDao.getInframodelAliases()

    @Test
    fun censoringAuthorWorks() {
        val xmlString = classpathResourceToString(TESTFILE_SIMPLE)
        assertTrue(xmlString.contains("Geoviite Test Author"))
        assertTrue(xmlString.contains("example@vayla.fi"))
        val censored = censorAuthorIdentifyingInfo(xmlString)
        assertFalse(censored.contains("Geoviite Test Author"))
        assertFalse(censored.contains("example@vayla.fi"))

    }

    private fun debugParseFile(file: File): GeometryPlan? {
        try {
            val result = unmarshaller.unmarshal(toSaxSource(fileToString(file))) as InfraModel
            val converted = toGvtPlan(
                FileName(file.name),
                result,
                coordinateSystemNameToSrid,
                switchStructuresByType,
                switchTypeNameAliases,
                trackNumberDao.getTrackNumberToIdMapping(),
            )
            val errors = validate(converted, featureTypes, switchStructuresById)
            if (errors.isEmpty()) {
                println("No validation issues found")
            } else {
                for (error in errors.filter { e -> e.errorType == ErrorType.VALIDATION_ERROR }) {
                    println(error)
                }
            }
            return converted
        } catch (e: Exception) {
            System.err.println("Parsing failed: file=${file.name} error=$e")
            e.printStackTrace()
            return null
        }
    }

    private fun getSubDirs(mainDir: File): List<File> {
        return (mainDir.listFiles { f -> f.isDirectory } ?: fail()).toList()
    }

    @Test
    fun fetchesTriangleInsideTriangulationNetwork() {
        // Point is in Hervanta, Tampere
        val triangles = kkJtoETRSTriangulationDao.fetchTriangulationNetwork()
        val point = toJtsPoint(Point(3332494.083, 6819936.144), KKJ3_YKJ)
        val triangle = triangles.find { it.intersects(point) }
        assertNotNull(triangle)
    }

    @Test
    fun fetchesTriangleAtCornerPoint() {
        // Point is in a triangulation network corner point
        val triangles = kkJtoETRSTriangulationDao.fetchTriangulationNetwork()
        val point = toJtsPoint(Point(3199159.097, 6747800.979), KKJ3_YKJ)
        val triangle = triangles.find { it.intersects(point) }
        assertNotNull(triangle)
    }

    @Test
    fun doesntFetchTriangleOutsideTriangulationNetwork() {
        // Point is in Norway
        val triangles = kkJtoETRSTriangulationDao.fetchTriangulationNetwork()
        val point = toJtsPoint(Point(2916839.212, 7227390.743), KKJ3_YKJ)
        val triangle = triangles.find { it.intersects(point) }
        assertNull(triangle)
    }

    @Test
    fun transformsYKJtoETRS() {
        val triangles = kkJtoETRSTriangulationDao.fetchTriangulationNetwork()
        // Point is in Hervanta, Tampere
        val point = Point(3332494.083, 6819936.144)
        val transform = Transformation(KKJ3_YKJ, LAYOUT_CRS, triangles = triangles)
        val transformedPoint = transform.transform(point)
        // Expected values are from paikkatietoikkuna
        assertEquals(332391.7884, transformedPoint.x, 0.001)
        assertEquals(6817075.2561, transformedPoint.y, 0.001)
    }

    @Test
    fun transformsKKJ4toETRS() {
        val kkj4point = Point(4488552.946177, 6943595.611588)
        val triangles = kkJtoETRSTriangulationDao.fetchTriangulationNetwork()
        val transform = Transformation(KKJ4, LAYOUT_CRS, triangles = triangles)
        val transformedPoint = transform.transform(kkj4point)
        // Expected values are from paikkatietoikkuna
        assertEquals(642412.7448, transformedPoint.x, 0.001)
        assertEquals(6943735.9093, transformedPoint.y, 0.001)
    }

    @Test
    fun decodeFile1() {
        // Insert a track number if does not exists already
        val trackNumber = getOrCreateTrackNumber(TrackNumber("001"))

        val xmlString = classpathResourceToString(TESTFILE_SIMPLE)
        val infraModel = stringToInfraModel(xmlString)
        assertEquals("finnish", infraModel.language)
        assertEquals("grads", infraModel.units?.metric?.angularUnit)
        assertEquals("grads", infraModel.units?.metric?.directionUnit)
        assertEquals("meter", infraModel.units?.metric?.linearUnit)
        assertEquals("ETRS89 / GK25FIN", infraModel.coordinateSystem?.name)
        assertEquals("2392", infraModel.coordinateSystem?.epsgCode)
        val converted = toGvtPlan(
            FileName(TESTFILE_SIMPLE),
            infraModel,
            coordinateSystemNameToSrid,
            switchStructuresByType,
            switchTypeNameAliases,
            trackNumberDao.getTrackNumberToIdMapping(),
        )
        val allAlignments = infraModel.alignmentGroups.flatMap { ag -> ag.alignments }
        assertTrackNumbersMatch(infraModel.alignmentGroups, trackNumber)
        assertAlignmentsMatch(allAlignments, converted.alignments)
        assertKmPostsMatch(allAlignments.flatMap { a -> a.staEquations }, converted.kmPosts)
    }

    @Test
    fun differentSpiralsCanBeParsed() {
        val (parsed, _) = parseFromClasspath(
            TESTFILE_CLOTHOID_AND_PARABOLA,
            coordinateSystemNameToSrid,
            switchStructuresByType,
            switchTypeNameAliases,
            trackNumberDao.getTrackNumberToIdMapping(),
        )
        val allElements = parsed.alignments.flatMap { a -> a.elements }
        assertEquals(29, allElements.filterIsInstance<GeometryClothoid>().size)
        assertEquals(8, allElements.filterIsInstance<BiquadraticParabola>().size)

        val allCantPoints = parsed.alignments.mapNotNull { a -> a.cant }.flatMap { c -> c.points }
        assertEquals(8, allCantPoints.filter { cp -> cp.transitionType == CantTransitionType.BIQUADRATIC_PARABOLA }.size)
    }

    @Test
    fun encodeAndDecodeWorks() {
        val infraModelObject = InfraModel(
            language = "finnish",
            featureDictionary = InfraModelFeatureDictionary("featureDictName"),
            units = InfraModelUnits(InfraModelMetric("meter", "squareMeter", "cubicMeter", "grads", "radians")),
            coordinateSystem = InfraModelCoordinateSystem("coordsys", "epsg1234"),
            alignmentGroups = listOf(
                InfraModelAlignmentGroup(
                    name = "name1",
                    desc = "desc1",
                    state = "proposed",
                    alignments = listOf(
                        InfraModelAlignment(
                            name = "alignment1",
                            desc = "desc1",
                            state = "proposed",
                            oid = "11",
                            staStart = "0.0",
                            elements = listOf(
                                InfraModelLine("l1", "o01", "123.123", "10.000", "start start", "end end"),
                                InfraModelCurve(
                                    "c1",
                                    "o11",
                                    "123.124",
                                    "11.000",
                                    "cw",
                                    "101.000",
                                    "1001.000",
                                    "start start",
                                    "center center",
                                    "end end"
                                ),
                                InfraModelSpiral(
                                    "s1",
                                    "o21",
                                    "123.125",
                                    "12.000",
                                    "clothoid",
                                    "ab",
                                    "2001.000",
                                    "3001.000",
                                    "3002.000",
                                    "4001.000",
                                    "INF",
                                    "start start",
                                    "pi pi",
                                    "end end"
                                ),
                                InfraModelLine("l2", "o02", "123.126", "13.000", "start start", "end end")
                            ),
                            cant = InfraModelCant(
                                "tc", "test cant", "1.01", "insideRail", listOf(
                                    InfraModelCantStation("12.12", "0.045000", "cw"),
                                    InfraModelCantStation("123.123", "0.055000", "ccw"),
                                    InfraModelCantStation("1234.1234", "0.065000", "cw"),
                                )
                            ),
                            profile = InfraModelProfile(
                                InfraModelProfAlign(
                                    "tp",
                                    listOf(
                                        InfraModelPvi("test start PVI", "100.001"),
                                        InfraModelCircCurve("test circcurve 1", "12.3", "654321", "123.321 456.654"),
                                        InfraModelCircCurve("test circcurve 2", "21.3", "123456", "321.123 654.456"),
                                        InfraModelPvi("test end PVI", "1000.5")
                                    ),
                                    listOf(
                                        InfraModelFeature(
                                            "profalignfeature",
                                            listOf(InfraModelProperty("testprop", "testvalue"))
                                        )
                                    )
                                ),
                                listOf(
                                    InfraModelFeature(
                                        "profilefeature",
                                        listOf(InfraModelProperty("testprop", "testvalue"))
                                    )
                                )
                            ),
                            features = listOf(
                                InfraModelFeature(
                                    "alignmentfeature",
                                    listOf(InfraModelProperty("testprop", "testvalue"))
                                )
                            ),
                            staEquations = listOf(
                                InfraModelStaEquation(
                                    staBack = "1003.440785",
                                    staAhead = "304.954785",
                                    staInternal = "304.954785",
                                    desc = "1",
                                    InfraModelFeature(
                                        code = "IM_kmPostCoords", listOf(
                                            InfraModelProperty(label = "northing", value = "6674007.758000"),
                                            InfraModelProperty(label = "easting", value = "25496599.876000")
                                        )
                                    )
                                )
                            )
                        ),
                        InfraModelAlignment(
                            name = "alignment2",
                            desc = "desc2",
                            state = "proposed",
                            oid = "12",
                            staStart = "10.0",
                            elements = listOf(
                                InfraModelLine("l3", "o03", "123.127", "20.000", "start start", "end end"),
                                InfraModelCurve(
                                    "c2",
                                    "o12",
                                    "123.128",
                                    "21.000",
                                    "ccw",
                                    "101.000",
                                    "1001.000",
                                    "start start",
                                    "center center",
                                    "end end"
                                ),
                                InfraModelSpiral(
                                    "s2",
                                    "o22",
                                    "123.129",
                                    "22.000",
                                    "clothoid",
                                    "ac",
                                    "2002.000",
                                    "3011.000",
                                    "3012.000",
                                    "4002.000",
                                    "INF",
                                    "start start",
                                    "pi pi",
                                    "end end"
                                ),
                                InfraModelLine("l4", "o04", "123.130", "23.000", "start start", "end end")
                            )
                        )
                    )
                )
            )
        )
        assertEquals("meter", infraModelObject.units?.metric?.linearUnit)
        val sw = StringWriter()
        marshaller.marshal(infraModelObject, sw)
        val xmlString = sw.toString()

        assertTrue(xmlString.startsWith("<?xml"))
        assertTrue(xmlString.contains("<LandXML"))
        assertTrue(xmlString.contains("<FeatureDictionary"))
        assertTrue(xmlString.contains("<Units><Metric"))
        assertTrue(xmlString.contains("<CoordinateSystem"))
        assertTrue(xmlString.contains("<Alignments"))
        assertTrue(xmlString.contains("<Alignment"))
        assertTrue(xmlString.contains("<CoordGeom"))
        assertTrue(xmlString.contains("<Line"))
        assertTrue(xmlString.contains("<Curve"))
        assertTrue(xmlString.contains("<Spiral"))

        val parsed = unmarshaller.unmarshal(toSaxSource(xmlString)) as InfraModel
        assertEquals(infraModelObject, parsed)
    }

    private fun assertTrackNumbersMatch(
        infraModelAlignmentGroups: List<InfraModelAlignmentGroup>,
        trackNumber: TrackLayoutTrackNumber?,
    ) {
        assertNotNull(trackNumber)
        assertEquals(infraModelAlignmentGroups.first().name, trackNumber?.number?.value)
    }

    private fun assertAlignmentsMatch(
        infraModelAlignments: List<InfraModelAlignment>,
        gvtAlignments: List<GeometryAlignment>,
    ) {
        assertEquals(infraModelAlignments.size, gvtAlignments.size)
        infraModelAlignments.forEachIndexed { aIndex, xmlAlignment ->
            val gvtAlignment = gvtAlignments[aIndex]
            assertEquals(xmlAlignment.name, gvtAlignment.name.value)
            assertEquals(xmlAlignment.desc, gvtAlignment.description?.value)
            assertEquals(xmlAlignment.elements.size, gvtAlignment.elements.size)
            xmlAlignment.elements.forEachIndexed { eIndex, xmlElement ->
                val gvtElement = gvtAlignment.elements[eIndex]
                assertEquals(xmlElement.name, gvtElement.name?.value)
                assertEquals(xmlElement.oID, gvtElement.oidPart?.value)
                when (xmlElement) {
                    is InfraModelLine -> assertTrue(gvtElement is GeometryLine)
                    is InfraModelCurve -> assertTrue(gvtElement is GeometryCurve)
                    is InfraModelSpiral -> {
                        when (xmlElement.spiType) {
                            "clothoid" -> assertTrue(gvtElement is GeometryClothoid)
                            "biquadraticParabola" -> assertTrue(gvtElement is BiquadraticParabola)
                            else -> fail("Unknown spiral type ${xmlElement.spiType}")
                        }
                    }
                }
            }
            assertEquals(xmlAlignment.profile != null, gvtAlignment.profile != null)
            if (xmlAlignment.profile != null && gvtAlignment.profile != null) {
                assertProfilesMatch(xmlAlignment.profile!!, gvtAlignment.profile!!)
            }
            assertEquals(xmlAlignment.cant != null, gvtAlignment.cant != null)
            if (xmlAlignment.cant != null && gvtAlignment.cant != null) {
                assertCantsMatch(xmlAlignment.cant!!, gvtAlignment.cant!!)
            }
            assertFeatureTypeCodeMatch(xmlAlignment.features, gvtAlignment.featureTypeCode)
        }
    }

    private fun assertFeatureTypeCodeMatch(features: List<InfraModelFeature>, featureTypeCode: FeatureTypeCode?) {
        val xmlCodeProperty = features.find { imFeature -> imFeature.code == "IM_coding" }
            ?.getPropertyAnyMatch(listOf("terrainCoding", "infraCoding"))
        assertEquals(xmlCodeProperty, featureTypeCode?.value)
    }

    private fun assertProfilesMatch(imProfile: InfraModelProfile, gvtProfile: GeometryProfile) {
        val profAlign = imProfile.profAlign!!
        assertEquals(profAlign.name, gvtProfile.name.value)
        assertEquals(profAlign.elements.size, gvtProfile.elements.size)
        profAlign.elements.forEachIndexed { i, xmlElement ->
            val gvtElement = gvtProfile.elements[i]
            assertEquals(xmlElement.desc, gvtElement.description.value)
        }
    }

    private fun assertCantsMatch(infraModelCant: InfraModelCant, gvtCant: GeometryCant) {
        assertEquals(infraModelCant.name, gvtCant.name.value)
        assertEquals(infraModelCant.desc, gvtCant.description.value)
        assertEquals(infraModelCant.gauge, gvtCant.gauge.toString())
        assertEquals(infraModelCant.stations.size, gvtCant.points.size)
        infraModelCant.stations.forEachIndexed { i, xmlStation ->
            val gvtPoint = gvtCant.points[i]
            assertEquals(xmlStation.station, gvtPoint.station.toString())
            assertEquals(xmlStation.curvature, gvtPoint.curvature.toString().lowercase())
            assertEquals(xmlStation.appliedCant, gvtPoint.appliedCant.toString())
        }
    }

    private fun assertKmPostsMatch(xmlKmPosts: List<InfraModelStaEquation>, gvtKmPost: List<GeometryKmPost>) {
        if (xmlKmPosts.isNotEmpty()) {
            gvtKmPost.forEachIndexed { i, gvtKmPostElement ->

                assertEquals(parseNullableBigDecimal(xmlKmPosts[i].staBack), gvtKmPostElement.staBack)
                assertEquals(parseNullableBigDecimal(xmlKmPosts[i].staAhead), gvtKmPostElement.staAhead)
                assertEquals(parseNullableBigDecimal(xmlKmPosts[i].staInternal), gvtKmPostElement.staInternal)
                assertEquals(parseKmNumber(xmlKmPosts[i].desc), gvtKmPostElement.kmNumber)

                val gvtKmPostLocation: Point? = gvtKmPostElement.location
                val xmlKmPostPoint = xmlKmPosts[i].feature?.let { f -> parseFeatureCoordinates(f) }
                assertEquals(gvtKmPostLocation == null, xmlKmPostPoint == null)
                if (gvtKmPostLocation != null && xmlKmPostPoint != null) {
                    assertApproximatelyEquals(gvtKmPostLocation, xmlKmPostPoint)
                }
            }
        }
    }

    private fun parseNullableBigDecimal(value: String): BigDecimal? {
        return if (value == "NaN") null else value.toBigDecimal()
    }

    private fun parseFeatureCoordinates(xmlFeature: InfraModelFeature): Point {
        val properties: List<InfraModelProperty> = xmlFeature.properties
        val coordinates = properties.map { p -> p.value.toBigDecimal() }
        return Point(coordinates[1].toDouble(), coordinates[0].toDouble())
    }
}
