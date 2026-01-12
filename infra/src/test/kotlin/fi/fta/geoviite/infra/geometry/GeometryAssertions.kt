import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.geometry.Application
import fi.fta.geoviite.infra.geometry.Author
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.geometry.Project
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

fun assertPlansMatch(original: GeometryPlan, planFromDb: GeometryPlan) {
    assertEquals(original.source, planFromDb.source)
    assertMatches(original.project, planFromDb.project)
    assertMatches(original.application, planFromDb.application)
    assertMatches(original.author, planFromDb.author)
    assertEquals(original.planTime, planFromDb.planTime)
    original.uploadTime?.let { uploadTime -> assertEquals(uploadTime, planFromDb.uploadTime) }
    assertEquals(original.units, planFromDb.units)
    assertEquals(original.trackNumber, planFromDb.trackNumber)
    assertEquals(original.trackNumberDescription, planFromDb.trackNumberDescription)
    assertEquals(original.fileName, planFromDb.fileName)
    assertEquals(original.pvDocumentId, planFromDb.pvDocumentId)
    assertEquals(original.planPhase, planFromDb.planPhase)
    assertEquals(original.decisionPhase, planFromDb.decisionPhase)
    assertEquals(original.measurementMethod, planFromDb.measurementMethod)
    assertEquals(original.elevationMeasurementMethod, planFromDb.elevationMeasurementMethod)
    assertEquals(original.message, planFromDb.message)
    assertEquals(original.name, planFromDb.name)
    assertEquals(original.isHidden, planFromDb.isHidden)
    assertEquals(original.planApplicability, planFromDb.planApplicability)

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
    assertEquals(original, fromDb.copy(id = original.id))
}

fun assertMatches(original: Author?, fromDb: Author?) {
    assertEquals(original?.copy(id = IntId(1)), fromDb?.copy(id = IntId(1)))
}

fun assertMatches(original: Application?, fromDb: Application?) {
    assertEquals(original?.copy(id = IntId(1)), fromDb?.copy(id = IntId(1)))
}

fun assertMatches(original: GeometryKmPost, fromDb: GeometryKmPost) {
    assertEquals(original, fromDb.copy(id = original.id))
}

fun assertMatches(original: GeometrySwitch, fromDb: GeometrySwitch) {
    assertEquals(original, fromDb.copy(id = original.id))
}

fun assertMatches(original: LayoutTrackNumber?, fromDb: LayoutTrackNumber?) {
    assertEquals(original?.number, fromDb?.number)
    assertEquals(original?.description, fromDb?.description)
    assertEquals(original?.state, fromDb?.state)
}

fun assertMatches(original: GeometryAlignment, fromDb: GeometryAlignment) {
    assertEquals(original.name, fromDb.name)
    assertEquals(original.description, fromDb.description)
    assertEquals(original.oidPart, fromDb.oidPart)
    assertEquals(original.state, fromDb.state)
    assertEquals(original.featureTypeCode, fromDb.featureTypeCode)
    assertEquals(original.staStart, fromDb.staStart)
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
