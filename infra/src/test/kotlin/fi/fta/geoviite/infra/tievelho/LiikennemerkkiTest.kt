package fi.fta.geoviite.infra.tievelho

import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.ifc.IfcName
import fi.fta.geoviite.infra.ifc.IfcTransformTemplate
import fi.fta.geoviite.infra.ifc.parseIfc
import fi.fta.geoviite.infra.ifc.transform
import getLiikenneMerkki
import org.junit.jupiter.api.Test
import java.io.File

class LiikennemerkkiTest {
    @Test
    fun `Creating liikennemerkki from IFC`() {
        val ifc = parseIfc(File("/home/jyrkija/Geoviite/IFC-liikennemerkki/bsdd.ifc"))
        val buildings = ifc.getDereferenced(IfcName.valueOf("IFCBUILDING"))
        println(buildings)

        val aggregates = ifc.getDereferenced(IfcName.valueOf("IFCRELAGGREGATES"))
        println(aggregates)

        // TODO: How this should actually go:
        // Each liikennemerkki is an object of type IFCBUILDINGELEMENTPROXY (here #952)
        // Each relDefinesByProperties links an object to a propertyset (the parser doesn't handle these yet)
        // If the object is a liikennemerkki, it will have the required propertysets (one with name='Liikennemerkit')

        // TODO: Why this code finds all relevant data (somewhat)
        // Since all that isn't implemented, we just pick the rel as root element, seeking by properyset name
        // - dereferencing digs out the buildingelementproxy too though not any other rels that might exist (but don't here)
        val relDefinesByProperties = ifc.getDereferenced(IfcName.valueOf("IFCRELDEFINESBYPROPERTIES"))
        val liikennemerkkiRoots =
            relDefinesByProperties.filter { r -> r.getStringField(5, 2).value == "Liikennemerkit" }
        val jsonTemplate = File("/home/jyrkija/Geoviite/IFC-liikennemerkki/template.json").readText()
        println(jsonTemplate)
        val transformTemplate = IfcTransformTemplate(jsonTemplate, null)
        liikennemerkkiRoots.forEach { entity ->
            println(entity)
            val transformed = transform(entity, transformTemplate)
            println(transformed)
        }
    }

    @Test
    fun `Generated liikennemerkki to JSON`() {
        println(ObjectMapper().writeValueAsString(getLiikenneMerkki()))

    }
}
