import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

fun assertPlansMatch(original: GeometryPlan, planFromDb: GeometryPlan) {
    assertMatches(original.project, planFromDb.project)
    assertMatches(original.author, planFromDb.author)
    assertMatches(original.application, planFromDb.application)
    assertEquals(original.units, planFromDb.units)
    assertEquals(original.fileName, planFromDb.fileName)

    assertEquals(original.trackNumber, planFromDb.trackNumber)
    assertEquals(original.trackNumberDescription, planFromDb.trackNumberDescription)

    assertEquals(original.alignments.size, planFromDb.alignments.size)
    original.alignments.forEachIndexed { index, convertedAlignment ->
        assertMatches(convertedAlignment, planFromDb.alignments[index])
    }

    assertEquals(original.switches.size, planFromDb.switches.size)
    original.switches.forEachIndexed { index, convertedSwitch ->
        assertMatches(convertedSwitch, planFromDb.switches[index])
    }

    assertEquals(original.kmPosts.size, planFromDb.kmPosts.size)
    original.kmPosts.forEachIndexed { index, convertedKmPost ->
        assertMatches(convertedKmPost, planFromDb.kmPosts[index])
    }
    assertEquals(original.bounds, planFromDb.bounds)
}

fun assertMatches(original: Project, fromDb: Project) {
    assertEquals(original.name, fromDb.name)
    assertEquals(original.description, fromDb.description)
}

fun assertMatches(original: Author?, fromDb: Author?) {
    assertEquals(original == null, fromDb == null)
    assertEquals(original?.companyName, fromDb?.companyName)
}

fun assertMatches(original: Application, fromDb: Application) {
    assertEquals(original.name, fromDb.name)
    assertEquals(original.manufacturer, fromDb.manufacturer)
    assertEquals(original.version, fromDb.version)
}

fun assertMatches(original: GeometryKmPost, fromDb: GeometryKmPost) {
    assertEquals(original.staBack, fromDb.staBack)
    assertEquals(original.staAhead, fromDb.staAhead)
    assertEquals(original.staInternal, fromDb.staInternal)
    assertEquals(original.kmNumber, fromDb.kmNumber)
    assertEquals(original.description, fromDb.description)
    assertEquals(original.location, fromDb.location)
    assertEquals(original.state, fromDb.state)
}

fun assertMatches(original: GeometrySwitch, fromDb: GeometrySwitch) {
    assertEquals(original.name, fromDb.name)
    assertEquals(original.switchStructureId, fromDb.switchStructureId)
    assertEquals(original.typeName, fromDb.typeName)
    assertEquals(original.state, fromDb.state)
    assertEquals(original.joints, fromDb.joints)
}

fun assertMatches(original: LayoutTrackNumber?, fromDb: LayoutTrackNumber?) {
    if (original != null || fromDb != null) {
        assertEquals(original?.number, fromDb?.number)
        assertEquals(original?.description, fromDb?.description)
        assertEquals(original?.state, fromDb?.state)
    }
}

fun assertMatches(original: GeometryAlignment, fromDb: GeometryAlignment) {
    assertEquals(original.name, fromDb.name)
    assertEquals(original.description, fromDb.description)
    assertEquals(original.staStart, fromDb.staStart)
    assertEquals(original.featureTypeCode, fromDb.featureTypeCode)
    assertEquals(original.profile, fromDb.profile)
    assertEquals(original.cant, fromDb.cant)

    assertEquals(original.elements.size, fromDb.elements.size)
    original.elements.forEachIndexed { index, convertedElement ->
        val elementFromDb = fromDb.elements[index]
        assertTrue(
            convertedElement.contentEquals(elementFromDb),
            "Contents should be equal: \n\texpect=$convertedElement. \n\tactual=$elementFromDb",
        )
    }
}
