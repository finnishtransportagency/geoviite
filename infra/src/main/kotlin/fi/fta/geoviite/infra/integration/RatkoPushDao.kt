package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.linking.Publication
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.*
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional(readOnly = true)
@Component
class RatkoPushDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    @Transactional
    fun startPushing(user: UserName, layoutPublishIds: List<IntId<Publication>>): IntId<RatkoPush> {
        val sql = """
            insert into integrations.ratko_push(start_time, status)
            values (now(), 'IN_PROGRESS')
            returning id
        """.trimIndent()

        jdbcTemplate.setUser(user)
        val ratkoPushId = jdbcTemplate.query(sql) { rs, _ ->
            rs.getIntId<RatkoPush>("id")
        }.first()

        updatePushContent(ratkoPushId, publicationIds = layoutPublishIds)

        return ratkoPushId.also { logger.daoAccess(AccessType.INSERT, RatkoPush::class, ratkoPushId) }
    }

    @Transactional
    fun finishStuckPushes(user: UserName) {
        val sql = """
            update integrations.ratko_push
            set 
                end_time = now(),
                status = case
                    when status = 'IN_PROGRESS' then 'FAILED'
                    when status = 'IN_PROGRESS_M_VALUES' then 'SUCCESSFUL'
                    else status
                end
            where end_time is null
            returning id
        """.trimIndent()

        jdbcTemplate.setUser(user)
        jdbcTemplate.query(sql) { rs, _ -> rs.getIntId<RatkoPush>("id") }.also { updatedPushes ->
            logger.daoAccess(AccessType.UPDATE, RatkoPush::class, updatedPushes)
        }
    }

    @Transactional
    fun updatePushStatus(user: UserName, pushId: IntId<RatkoPush>, status: RatkoPushStatus) {
        val sql = """
            update integrations.ratko_push
            set 
              end_time = case when :status = 'IN_PROGRESS_M_VALUES' then null else now() end,
              status = :status::integrations.ratko_push_status
            where id = :push_id
        """.trimIndent()

        val params = mapOf(
            "push_id" to pushId.intValue,
            "status" to status.name,
        )

        jdbcTemplate.setUser(user)
        check(jdbcTemplate.update(sql, params) == 1)
        logger.daoAccess(AccessType.UPDATE, RatkoPush::class, pushId)
    }

    fun fetchPreviousPush(): RatkoPush {
        val sql = """
            select
              id,
              start_time,
              end_time,
              status
            from integrations.ratko_push
            order by end_time desc
            limit 1
        """.trimIndent()

        return jdbcTemplate.query(sql) { rs, _ ->
            RatkoPush(
                id = rs.getIntId("id"),
                startTime = rs.getInstant("start_time"),
                endTime = rs.getInstantOrNull("end_time"),
                status = rs.getEnum("status"),
            )
        }.first().also {
            logger.daoAccess(AccessType.FETCH, RatkoPush::class, it)
        }
    }

    private fun updatePushContent(
        pushId: IntId<RatkoPush>,
        publicationIds: List<IntId<Publication>>
    ) {
        require(publicationIds.isNotEmpty())

        val sql = """
            insert into integrations.ratko_push_content
              values (:publication_id, :ratko_push_id)
            on conflict (publication_id) 
              do update set ratko_push_id = :ratko_push_id
        """.trimIndent()

        val params = publicationIds.map { id ->
            mapOf(
                "ratko_push_id" to pushId.intValue,
                "publication_id" to id.intValue,
            )
        }.toTypedArray()

        check(jdbcTemplate.batchUpdate(sql, params).isNotEmpty())

        logger.daoAccess(AccessType.UPSERT, RatkoPush::class, pushId, publicationIds)
    }

    @Transactional
    fun <T> insertRatkoPushError(
        ratkoPushId: IntId<RatkoPush>,
        ratkoPushErrorType: RatkoPushErrorType,
        operation: RatkoOperation,
        assetType: RatkoAssetType,
        assetId: IntId<T>,
        responseBody: String,
    ): IntId<RatkoPushError<T>> {
        //language=SQL
        val sql = """
            insert into integrations.ratko_push_error(
              ratko_push_id,
              track_number_id,
              location_track_id,
              switch_id,
              error_type,
              operation,
              response_body
            )
            values(
              :ratko_push_id, 
              :track_number_id, 
              :location_track_id, 
              :switch_id,
              :error_type::integrations.ratko_push_error_type, 
              :operation::integrations.ratko_push_error_operation,
              :response_body
            )
            returning id
        """.trimIndent()
        val params = mapOf(
            "ratko_push_id" to ratkoPushId.intValue,
            "error_type" to ratkoPushErrorType.name,
            "operation" to operation.name,
            "track_number_id" to if (assetType == RatkoAssetType.TRACK_NUMBER) assetId.intValue else null,
            "location_track_id" to if (assetType == RatkoAssetType.LOCATION_TRACK) assetId.intValue else null,
            "switch_id" to if (assetType == RatkoAssetType.SWITCH) assetId.intValue else null,
            "response_body" to responseBody,
        )

        return jdbcTemplate.queryOne<IntId<RatkoPushError<T>>>(sql, params, ratkoPushId.toString()) { rs, _ ->
            rs.getIntId("id")
        }.also { errorId -> logger.daoAccess(AccessType.INSERT, RatkoPushError::class, errorId) }
    }

    fun getLatestRatkoPushErrorFor(publishId: IntId<Publication>): RatkoPushError<*>? {
        //language=SQL
        val sql = """
            select 
              ratko_push_error.id, 
              error_type, 
              operation, 
              ratko_push_id, 
              track_number_id, 
              location_track_id, 
              switch_id 
            from integrations.ratko_push_error
              inner join integrations.ratko_push
                on ratko_push.id = ratko_push_error.ratko_push_id
              inner join integrations.ratko_push_content 
                using(ratko_push_id)
            where ratko_push_content.publication_id = :id
            order by ratko_push.end_time desc, ratko_push_error.id desc
            limit 1;
        """.trimIndent()

        return jdbcTemplate.queryOptional(sql, mapOf("id" to publishId.intValue)) { rs, _ ->
            val errorId = rs.getIntId<RatkoPushError<*>>("id")
            val trackNumberId = rs.getIntIdOrNull<TrackLayoutTrackNumber>("track_number_id")
            val locationTrackId = rs.getIntIdOrNull<LocationTrack>("location_track_id")
            val switchId = rs.getIntIdOrNull<TrackLayoutSwitch>("switch_id")
            RatkoPushError(
                ratkoPushId = rs.getIntId("ratko_push_id"),
                ratkoPushErrorType = rs.getEnum("error_type"),
                operation = rs.getEnum("operation"),
                assetId = trackNumberId
                    ?: locationTrackId
                    ?: switchId
                    ?: throw IllegalStateException("Encountered Ratko push error without asset! id: $errorId"),
                assetType = trackNumberId?.let { RatkoAssetType.TRACK_NUMBER }
                    ?: locationTrackId?.let { RatkoAssetType.LOCATION_TRACK }
                    ?: switchId.let { RatkoAssetType.SWITCH }
            )
        }?.also { pushError -> logger.daoAccess(AccessType.FETCH, RatkoPushError::class, pushError) }
    }

    fun getLatestPushedPublicationMoment(): Instant {
        //language=SQL
        val sql = """
            select 
              coalesce(max(publication.publication_time), '2000-01-01 00:00:00'::timestamptz) as latest_publication_time
            from integrations.ratko_push
            inner join integrations.ratko_push_content on ratko_push_content.ratko_push_id = ratko_push.id
            inner join publication.publication on publication.id = ratko_push_content.publication_id
            where ratko_push.status = 'SUCCESSFUL'
        """.trimIndent()

        return jdbcTemplate.queryOne(sql) { rs, _ ->
            rs.getInstant("latest_publication_time")
        }.also { logger.daoAccess(AccessType.FETCH, Publication::class) }
    }

    fun getLatestPublicationMoment(): Instant {
        //language=SQL
        val sql = """
            select coalesce(max(publication.publication_time), now()) as latest_publication_time
            from publication.publication
        """.trimIndent()
        return jdbcTemplate.queryOne(sql) { rs, _ -> rs.getInstant("latest_publication_time") }
            .also { logger.daoAccess(AccessType.FETCH, Publication::class) }
    }

    fun getRatkoStatus(publicationId: IntId<Publication>): List<RatkoPush> {
        val sql = """
            select
              ratko_push.id,
              ratko_push.start_time,
              ratko_push.end_time,
              ratko_push.status
            from integrations.ratko_push
            inner join integrations.ratko_push_content on ratko_push_content.ratko_push_id = ratko_push.id
            inner join publication.publication on publication.id = ratko_push_content.publication_id
            where publication.id = :publication_id
        """.trimIndent()

        return jdbcTemplate.query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
            RatkoPush(
                id = rs.getIntId("id"),
                startTime = rs.getInstant("start_time"),
                endTime = rs.getInstantOrNull("end_time"),
                status = rs.getEnum("status"),
            )
        }.onEach { push -> logger.daoAccess(AccessType.FETCH, RatkoPush::class, push.id) }
    }
}
