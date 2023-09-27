package fi.fta.geoviite.infra.ifc

import fi.fta.geoviite.infra.ifc.IfcDataTransformType.JSON_LIST
import fi.fta.geoviite.infra.ifc.IfcTransformTemplate.Companion.propertyTemplate
import org.junit.jupiter.api.Test
import java.math.BigDecimal
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
        val entity1 = IfcDataEntity(
            IfcEntityId("#1"), IfcEntity(
                name = IfcName.valueOf("TEST_ENTITY"), content = IfcEntityList(
                    IfcEntityEnum.valueOf("TEST_ENUM"),
                    IfcEntityNumber(BigDecimal.ONE),
                    IfcEntityList(IfcEntityNumber(BigDecimal.TEN), IfcEntityString("Inner string value")),
                    IfcEntityString("Outer string value"),
                )
            )
        )
        val entity2 = IfcDataEntity(
            IfcEntityId("#2"), IfcEntity(
                name = IfcName.valueOf("TEST_ENTITY"), content = IfcEntityList(
                    IfcEntityEnum.valueOf("TEST_ENUM_2"),
                    IfcEntityNumber(BigDecimal.ZERO),
                    IfcEntityList(IfcEntityMissingValue.UNSET, IfcEntityMissingValue.UNSET),
                    IfcEntityString("Outer string value 2"),
                )
            )
        )

        val differentEntity = IfcDataEntity(
            IfcEntityId("#3"), IfcEntity(
                name = IfcName.valueOf("TEST_ENTITY_2"), content = IfcEntityList(
                    IfcEntityEnum.valueOf("TEST_ENUM"),
                    IfcEntityNumber(BigDecimal.ZERO),
                )
            )
        )

        val ifc = Ifc(header = IfcHeader(), data = IfcData(entity1, entity2, differentEntity))

    }

//    @Test
//    fun `Transforms are found from JSON string`() {
////        println(transformRegexString)
//        val props = listOf(
//            listOf("asdf") to listOf(0),
//            listOf("asdf", "asd", "f") to listOf(0, 0, 1),
//            listOf("a", "b", "c") to listOf(1, 2, 3),
//            listOf("d", "e", "f") to listOf(4, 5, 6),
//            listOf("x", "y", "z") to listOf(21, 22, 23),
//        )
//        val json = """
//        {
//          "title": "${propertyTemplate(props[0].first)}",
//          "name": "${propertyTemplate(props[1].first)}",
//          "description": "NO_OP:${propertyTemplate(props[2].first)},${propertyTemplate(props[3].first)}",
//          "inner-obj": {
//            "inner-field": "${propertyTemplate(props[4].first)}"
//          }
//        }
//        """.trimIndent()
//        println(json)
//        val template = IfcTransformTemplate(json) { chain ->
//            props.find { p -> p.first == chain }?.second
//                ?: throw IllegalArgumentException("Unknown property chain requested: $props")
//        }
//        println(template.transforms)
//        println(
//            template.toJson(
//                mapOf(
//                    "${propertyTemplate(props[0].first)}" to "ASDF",
//                    "${propertyTemplate("asdf.asd.f")}" to "ASDF->ASD->F",
//                    "${propertyTemplate("NO_OP:a.b.c,d.e.f")}" to "ABCDEF",
//                    "${propertyTemplate("x.y.z")}" to "INNER XYZ",
//                )
//            )
//        )
//    }
}
