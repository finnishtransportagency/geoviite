package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutState.*
import org.junit.jupiter.api.Assertions.*
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

        assertTrue(kmPostDao.officialKmPostExists(officialId))

        val post2 = TrackLayoutKmPost(KmNumber(432), Point(123.4, 234.5), IN_USE, trackNumberId, null)
        val draftId = kmPostDao.insert(draft(post2)).id

        assertFalse(kmPostDao.officialKmPostExists(draftId))
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
