package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutState.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutKmPostDaoIT @Autowired constructor(
    private val kmPostDao: LayoutKmPostDao,
): ITTestBase() {

    @Test
    fun kmPostsAreStoredAndLoadedOk() {
        val trackNumberId = insertOfficialTrackNumber()
        val post1 = TrackLayoutKmPost(KmNumber(123), Point(123.4, 234.5), IN_USE, trackNumberId, null)
        val post2 = TrackLayoutKmPost(KmNumber(125), Point(125.6, 236.7), NOT_IN_USE, trackNumberId, null)
        val post3 = TrackLayoutKmPost(KmNumber(124), Point(124.5, 235.6), PLANNED, trackNumberId, null)
        insertAndVerify(post1)
        insertAndVerify(post2)
        insertAndVerify(post3)
        val allPosts = fetchTrackNumberKmPosts(OFFICIAL, trackNumberId)
        assertEquals(3, allPosts.size)
        assertMatches(post1, allPosts[0])
        assertMatches(post2, allPosts[2]) // The order should be by post-number, not insert order
        assertMatches(post3, allPosts[1])
    }

    @Test
    fun checkingIfKmPostIsOfficialWorks() {
        val trackNumberId = insertOfficialTrackNumber()
        val post1 = TrackLayoutKmPost(KmNumber(234), Point(123.4, 234.5), IN_USE, trackNumberId, null)
        val officialId = kmPostDao.insert(post1).id

        assertEquals(officialId, kmPostDao.fetchOfficialVersion(officialId)?.id)

        val post2 = TrackLayoutKmPost(KmNumber(432), Point(123.4, 234.5), IN_USE, trackNumberId, null)
        val draftId = kmPostDao.insert(draft(post2)).id

        assertNull(kmPostDao.fetchOfficialVersion(draftId)?.id)
    }

    @Test
    fun kmPostVersioningWorks() {
        val trackNumberId = insertOfficialTrackNumber()
        val tempPost = kmPost(trackNumberId, KmNumber(1), Point(1.0, 1.0))
        val insertVersion = kmPostDao.insert(tempPost)
        val inserted = kmPostDao.fetch(insertVersion)
        assertMatches(tempPost, inserted)
        assertEquals(VersionPair(insertVersion, null), kmPostDao.fetchVersionPair(insertVersion.id))

        val tempDraft1 = draft(inserted).copy(location = Point(2.0, 2.0))
        val draftVersion1 = kmPostDao.insert(tempDraft1)
        val draft1 = kmPostDao.fetch(draftVersion1)
        assertMatches(tempDraft1, draft1)
        assertEquals(VersionPair(insertVersion, draftVersion1), kmPostDao.fetchVersionPair(insertVersion.id))

        val tempDraft2 = draft1.copy(location = Point(3.0, 3.0))
        val draftVersion2 = kmPostDao.update(tempDraft2)
        val draft2 = kmPostDao.fetch(draftVersion2)
        assertMatches(tempDraft2, draft2)
        assertEquals(VersionPair(insertVersion, draftVersion2), kmPostDao.fetchVersionPair(insertVersion.id))

        kmPostDao.deleteDrafts(insertVersion.id)
        assertEquals(VersionPair(insertVersion, null), kmPostDao.fetchVersionPair(insertVersion.id))

        assertEquals(inserted, kmPostDao.fetch(insertVersion))
        assertEquals(draft1, kmPostDao.fetch(draftVersion1))
        assertEquals(draft2, kmPostDao.fetch(draftVersion2))
    }

    fun insertAndVerify(post: TrackLayoutKmPost) {
        val id = kmPostDao.insert(post)
        assertMatches(post, kmPostDao.fetch(id))
    }

    private fun fetchTrackNumberKmPosts(
        publishType: PublishType,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): List<TrackLayoutKmPost> {
        return kmPostDao.fetchVersions(publishType, trackNumberId).map(kmPostDao::fetch)
    }
}
