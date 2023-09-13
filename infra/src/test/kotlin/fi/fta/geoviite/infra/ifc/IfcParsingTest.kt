package fi.fta.geoviite.infra.ifc

import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.Path

class IfcParsingTest {

    @Test
    fun testParsing() {
        for (file in listOf(
            "Hierarkiasilta.ifc",
            "Case 1 validoitu 10102018.ifc",
//            "Case 2 validoitu 11102018.ifc",
//            "Case 2 validoitu 20190408.ifc",
//            "Case 2 validoitu 20200115.ifc",
//            "Case 3 validoitu 10102018.ifc",
        ))
//            try {
        {
            val filePath = "/home/jyrkija/Geoviite/IFC-mallit/$file"
            val attributes = Files.readAttributes(Path(filePath), BasicFileAttributes::class.java)
            println("Parsing IFC: path=$filePath ${attributes.size()}")
            val parsed = parseIfcFromPath(filePath)
//            println(parsed)
            println("done")
        }
//        catch (e: Throwable) {
//            println(e)
//        }
    }
}
