package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.tracklayout.LayoutState.IN_USE
import fi.fta.geoviite.infra.tracklayout.LayoutState.NOT_IN_USE
import fi.fta.geoviite.infra.tracklayout.LocationTrackType.MAIN
import fi.fta.geoviite.infra.tracklayout.LocationTrackType.SIDE
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LocationTrackDaoIT @Autowired constructor(
    private val alignmentDao: LayoutAlignmentDao,
    private val locationTrackDao: LocationTrackDao,
): ITTestBase() {

    @Test
    fun locationTrackSaveAndLoadWorks() {
        val alignment = alignment()
        val alignmentVersion = alignmentDao.insert(alignment)
        val locationTrack = locationTrack(insertOfficialTrackNumber(), alignment).copy(
            name = AlignmentName("ORIG"),
            description = FreeText("Oridinal location track"),
            type = MAIN,
            state = IN_USE,
            alignmentVersion = alignmentVersion,
        )

        val version = locationTrackDao.insert(locationTrack)
        assertEquals(version, locationTrackDao.fetchVersion(version.id, OFFICIAL))
        assertEquals(version, locationTrackDao.fetchVersion(version.id, DRAFT))
        val fromDb = locationTrackDao.fetch(version)
        assertMatches(locationTrack, fromDb)

        val updatedTrack = fromDb.copy(
            name = AlignmentName("UPD"),
            description = FreeText("Updated location track"),
            type = SIDE,
            state = NOT_IN_USE,
            topologicalConnectivity = TopologicalConnectivityType.END
        )
        val updatedVersion = locationTrackDao.update(updatedTrack)
        assertEquals(updatedVersion.id, version.id)
        assertEquals(updatedVersion, locationTrackDao.fetchVersion(version.id, OFFICIAL))
        assertEquals(updatedVersion, locationTrackDao.fetchVersion(version.id, DRAFT))
        val updatedFromDb = locationTrackDao.fetch(updatedVersion)
        assertMatches(updatedTrack, updatedFromDb)
    }

    @Test
    fun locationTrackExternalIdIsUnique() {
        val oid = Oid<LocationTrack>("99.99.99.99.99.99")

        // If the OID is already in use, remove it
        transactional {
            val deleteSql = "delete from layout.location_track where external_id = :external_id"
            jdbc.update(deleteSql, mapOf("external_id" to oid))
        }

        val trackNumberId = insertOfficialTrackNumber()
        val alignmentVersion1 = alignmentDao.insert(alignment())
        val locationTrack1 = locationTrack(trackNumberId).copy(externalId = oid, alignmentVersion = alignmentVersion1)
        val alignmentVersion2 = alignmentDao.insert(alignment())
        val locationTrack2 = locationTrack(trackNumberId).copy(externalId = oid, alignmentVersion = alignmentVersion2)

        locationTrackDao.insert(locationTrack1)
        assertThrows<DuplicateKeyException> { locationTrackDao.insert(locationTrack2) }
    }
}
