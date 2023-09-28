package fi.fta.geoviite.infra.ifc

import fi.fta.geoviite.infra.ifc.IfcDataTransformType.JSON_LIST
import fi.fta.geoviite.infra.ifc.IfcEntityMissingValue.UNSET
import fi.fta.geoviite.infra.ifc.IfcTransformTemplate.Companion.propertyTemplate
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IfcTransformTest {

    @Test
    fun `JSON template property mapping works by index without classification`() {
        val props = listOf(
            propertyTemplate("0") to "ASDF",
            propertyTemplate("0.0.1") to "ASDF->ASD->F",
            propertyTemplate("JSON_LIST:1.0.0,1.0.1") to "JSON_LIST(OneZeroZero, OneZeroOne)",
            propertyTemplate("2.0.0") to "TwoZeroZero",
        )
        val templateJson = """
        {
          "title": "${props[0].first}",
          "name": "${props[1].first}",
          "description": "${props[2].first}",
          "inner-obj": {
            "inner-field": "${props[3].first}"
          }
        }
        """.trimIndent()
        val resultJson =
            "{\"title\":\"${props[0].second}\",\"name\":\"${props[1].second}\",\"description\":\"${props[2].second}\",\"inner-obj\":{\"inner-field\":\"${props[3].second}\"}}"

        val template = IfcTransformTemplate(templateJson, null)
        assertEquals(
            listOf(
                IfcPropertyTransform(props[0].first, null, listOf(listOf(0))),
                IfcPropertyTransform(props[1].first, null, listOf(listOf(0, 0, 1))),
                IfcPropertyTransform(props[2].first, JSON_LIST, listOf(listOf(1, 0, 0), listOf(1, 0, 1))),
                IfcPropertyTransform(props[3].first, null, listOf(listOf(2, 0, 0))),
            ), template.transforms
        )
        assertEquals(resultJson, template.toJson(props.associate { it }))
    }

    @Test
    fun `JSON template is filled from IFC via indices`() {
        val name = "TEST_ENTITY"

        val entity1 = ifcDataEntity(
            id = 1,
            name = name,
            ifcEnum("TEST_ENUM"),
            ifcNumber(1),
            IfcEntityList(ifcNumber(10), ifcString("Inner value 1")),
            ifcString("Outer value 1"),
            ifcId(4), // Reference to referencedEntity
        )
        val entity2 = ifcDataEntity(
            id = 2,
            name = name,
            ifcEnum("TEST_ENUM_2"),
            ifcNumber(2),
            IfcEntityList(UNSET, UNSET),
            ifcString("Outer value 2"),
            ifcId(4),
        )

        val differentEntity = ifcDataEntity(
            id = 3,
            name = "OTHER_NAME",
            ifcEnum("OTHER_ENUM"),
        )
        val referencedEntity = ifcDataEntity(
            id = 4,
            name = "REFERENCED",
            ifcString("Value from ref"),
        )

        val ifc = ifc(entity1, entity2, differentEntity, referencedEntity)
        val template = """
            {
              "type": "${propertyTemplate("0")}",
              "number": "${propertyTemplate("1")}",
              "outer": "${propertyTemplate("3")}",
              "inner": "${propertyTemplate("2.1")}",
              "referenced": "${propertyTemplate("4.0")}"
            }
        """.trimIndent()
        val expectedJson1 =
            "{\"type\":\"TEST_ENUM\",\"number\":1,\"outer\":\"Outer value 1\",\"inner\":\"Inner value 1\",\"referenced\":\"Value from ref\"}"
        val expectedJson2 =
            "{\"type\":\"TEST_ENUM_2\",\"number\":2,\"outer\":\"Outer value 2\",\"inner\":null,\"referenced\":\"Value from ref\"}"
        val transformed = transform(ifc, ifcName(name), template, null)
        assertEquals(listOf(expectedJson1, expectedJson2), transformed)
    }
}
