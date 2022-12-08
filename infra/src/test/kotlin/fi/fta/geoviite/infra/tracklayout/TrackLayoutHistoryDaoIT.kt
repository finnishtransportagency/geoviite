package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.IntId
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@ActiveProfiles("dev", "test")
@SpringBootTest
class TrackLayoutHistoryDaoIT @Autowired constructor(
    val trackNumberDao: LayoutTrackNumberDao,
    val trackLayoutHistoryDao: TrackLayoutHistoryDao,
): ITTestBase() {

    @Test
    fun shouldRunFetchLocationTrackAtMomentSqlQueryWithoutErrors() {
        // Testing SQL query only
        trackLayoutHistoryDao.fetchLocationTrackAtMoment(
            IntId(1),
            Instant.now(),
        )
        trackLayoutHistoryDao.fetchLocationTrackAtMoment(
            IntId(1),
        )
    }

    @Test
    fun shouldRunFetchTrackNumberAtMomentSqlQueryWithoutErrors() {
        // Testing SQL query only
        trackLayoutHistoryDao.fetchTrackNumberAtMoment(
            IntId(1),
            Instant.now(),
        )
        trackLayoutHistoryDao.fetchTrackNumberAtMoment(
            IntId(1),
        )
    }

    @Test
    fun shouldRunFetchKmPostsAtMomentSqlQueryWithoutErrors() {
        // Testing SQL query only
        trackLayoutHistoryDao.fetchKmPostsAtMoment(
            IntId(1),
            Instant.now(),
        )
        trackLayoutHistoryDao.fetchKmPostsAtMoment(
            IntId(1),
        )
    }

    @Test
    fun shouldRunFetchReferenceLineAtMomentSqlQueryWithoutErrors() {
        // Testing SQL query only
        trackLayoutHistoryDao.fetchReferenceLineAtMoment(
            IntId(1),
            Instant.now(),
        )
        trackLayoutHistoryDao.fetchReferenceLineAtMoment(
            IntId(1),
        )
    }

    @Test
    fun shouldRunFetchSwitchAtMomentSqlQueryWithoutErrors() {
        // Testing SQL query only
        trackLayoutHistoryDao.getSwitchAtMoment(
            IntId(1),
            Instant.now(),
        )

        // Testing SQL query only
        trackLayoutHistoryDao.getSwitchAtMoment(
            IntId(2),
        )
    }

}
