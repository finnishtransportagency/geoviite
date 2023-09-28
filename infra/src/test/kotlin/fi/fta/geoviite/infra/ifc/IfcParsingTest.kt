package fi.fta.geoviite.infra.ifc

import fi.fta.geoviite.infra.util.FileName
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.time.Instant
import kotlin.io.path.Path
import kotlin.test.assertEquals

class IfcParsingTest {

    @Test
    fun `Basic IFC parsing works`() {
        val ifcString = """
            ISO-10303-21;
            HEADER;
            FILE_DESCRIPTION (('ViewDefinition[CoordinationView_V2.0]'), '2;1');
            FILE_NAME ('Parsing test file.ifc', '2023-09-28T11:38:00', ('Test Author', 'test.author@vayla.fi'), ('FTA'), 'Text editor: 1.0', 'Geoviite 1.0', $);
            FILE_SCHEMA (('IFC2X3'));
            ENDSEC;
            DATA;
            #1 = IFCTESTENTITY1(0.1,'String value 1',.ENUM_VALUE_1.,#3,(#4,#5),$,*,123456);
            #2 = IFCTESTENTITY1(1,'String value 2',.ENUM_VALUE_2.,#3,(#5,#6),$,*,654321);
            #3 = IFCTESTSUBENTITY1(1, 'Sub entity value 3');
            #4 = IFCTESTSUBENTITY2(('sublist item 1', 'sublist item 2'), 3);
            #5 = IFCTESTSUBENTITY2(('string with parenthesis ()', 'string with quotes '''), 4);
            #6 = IFCTESTSUBENTITY2($, 5);
            ENDSEC;
            END-ISO-10303-21;
        """.trimIndent()
        val parsed = parseIfc(ifcString.toByteArray(Charsets.UTF_8), Charsets.UTF_8)

        // Header
        assertEquals(FileName("Parsing test file.ifc"), parsed.header.fileName)
        assertEquals(
            ifcString("Test Author"),
            parsed.header.getElement(IfcHeader.fileNameHeader)?.getStringField(2, 0),
        )

        // Basic types
        val row1 = parsed.get(ifcId(1))
        assertEquals(
            "IFCTESTENTITY1(0.1,'String value 1',.ENUM_VALUE_1.,#3,(#4,#5),$,*,123456)",
            row1.toString(),
        )
        assertEquals(ifcNumber(0.1), row1.getNumberField(0))
        assertEquals(ifcString("String value 1"), row1.getStringField(1))
        assertEquals(ifcEnum("ENUM_VALUE_1"), row1.getEnumField(2))
        assertEquals(ifcId(3), row1.getIdField(3))
        assertEquals(IfcEntityList(ifcId(4), ifcId(5)), row1.getListField(4))
        assertEquals(null, row1.getNullableIdField(5))
        assertEquals(null, row1.getNullableIdField(6))
        assertEquals(ifcNumber(123456), row1.getNullableNumberField(7))

        // Escaping & lists-in-lists
        val row5 = parsed.get(ifcId(5))
        assertEquals(
            "IFCTESTSUBENTITY2(('string with parenthesis ()','string with quotes '''),4)",
            row5.toString(),
        )
        assertEquals(ifcString("string with parenthesis ()"), row5.getStringField(0, 0))
        assertEquals(ifcString("string with quotes '"), row5.getStringField(0, 1))

        // Dereferenced sub-entities
        val dereferencedRows = parsed.getDereferenced(ifcName("IFCTESTENTITY1"))
        assertEquals(2, dereferencedRows.size)
        val row3 = parsed.get(ifcId(3))
        assertEquals("IFCTESTSUBENTITY1(1,'Sub entity value 3')", row3.toString())
        assertEquals(ifcString("Sub entity value 3"), row3.getStringField(1))
        dereferencedRows.forEach { dereferenced ->
            assertEquals(row3, dereferenced.getEntityField(3))
            assertEquals(ifcString("Sub entity value 3"), dereferenced.getStringField(3, 1))
        }
    }

    @Disabled
    @Test
    fun `Test parsing local files`() {
        for (file in listOf(
            "Hierarkiasilta.ifc",
            "Case 1 validoitu 10102018.ifc",
            "Case 2 validoitu 11102018.ifc",
            "Case 2 validoitu 20190408.ifc",
            "Case 2 validoitu 20200115.ifc",
            "Case 3 validoitu 10102018.ifc",
        )) {
            val filePath = "../../IFC-mallit/$file"
            val attributes = Files.readAttributes(Path(filePath), BasicFileAttributes::class.java)
            println("Parsing IFC: path=$filePath ${attributes.size()}")
            val startTime = Instant.now()
            val parsed = parseIfcFromPath(filePath)
            val duration = Duration.between(startTime, Instant.now())
            println(
                "Done parsing: duration=${duration} dataLines=${parsed.data.linesById.size} header=${parsed.header.contentLines}"
            )
            println("Projects: ${parsed.getDereferenced(IfcName.valueOf("IFCPROJECT"))}")
            println(countData(parsed.data.linesById.map { (_, v) -> v.content }))
            println("Cache counts: id=${IfcEntityId.cacheSize} enum=${IfcEntityEnum.cacheSize} name=${IfcName.cacheSize}")
        }
    }

    private fun countData(values: List<IfcEntityAttribute>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        val plusOne = { _: String, c: Int? -> (c ?: 0) + 1 }
        values.forEach { v ->
            when (v) {
                is IfcEntity -> {
                    counts.compute("ENTITY", plusOne)
                    countData(v.content.items).forEach { (k, v) -> counts.compute(k) { _, c -> (c ?: 0) + v } }
                }

                is IfcEntityList -> {
                    counts.compute("LIST", plusOne)
                    countData(v.items).forEach { (k, v) -> counts.compute(k) { _, c -> (c ?: 0) + v } }
                }

                is IfcEntityId -> counts.compute("ID", plusOne)
                is IfcEntityNumber -> counts.compute("NUMBER", plusOne)
                is IfcEntityString -> counts.compute("STRING", plusOne)
                is IfcEntityEnum -> counts.compute("ENUM", plusOne)
                is IfcEntityMissingValue -> counts.compute("NULL", plusOne)
                else -> counts.compute("OTHER", plusOne)
            }
        }
        return counts
    }
}
