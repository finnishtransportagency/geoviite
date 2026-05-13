package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.integration.RatkoAssetRef
import fi.fta.geoviite.infra.integration.RatkoErrorData
import fi.fta.geoviite.infra.integration.RatkoLocationTrackRef
import fi.fta.geoviite.infra.integration.RatkoOperation
import fi.fta.geoviite.infra.integration.RatkoPush
import fi.fta.geoviite.infra.integration.RatkoPushAssetError
import fi.fta.geoviite.infra.integration.RatkoPushError
import fi.fta.geoviite.infra.integration.RatkoPushErrorType
import fi.fta.geoviite.infra.integration.RatkoPushGeneralError
import fi.fta.geoviite.infra.integration.RatkoPushStatus
import fi.fta.geoviite.infra.integration.RatkoSwitchRef
import fi.fta.geoviite.infra.integration.RatkoTrackNumberRef
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getEnumOrNull
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.getInstantOrNull
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.getOidOrNull
import fi.fta.geoviite.infra.util.produceIf
import fi.fta.geoviite.infra.util.queryOne
import fi.fta.geoviite.infra.util.queryOptional
import fi.fta.geoviite.infra.util.setUser
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional(readOnly = true)
@Component
class RatkoPushDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    @Transactional
    fun startPushing(layoutPublicationIds: List<IntId<Publication>>): IntId<RatkoPush> {
        val sql =
            """
            insert into integrations.ratko_push(start_time, status)
            values (now(), 'IN_PROGRESS')
            returning id
            """
                .trimIndent()

        jdbcTemplate.setUser()
        val ratkoPushId = jdbcTemplate.query(sql) { rs, _ -> rs.getIntId<RatkoPush>("id") }.first()

        updatePushContent(ratkoPushId, publicationIds = layoutPublicationIds)

        return ratkoPushId.also { logger.daoAccess(AccessType.INSERT, "${RatkoPush::class}.id", ratkoPushId) }
    }

    @Transactional
    fun finishStuckPushes() {
        val sql =
            """
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
            """
                .trimIndent()

        jdbcTemplate.setUser()
        val updatedPushes = jdbcTemplate.query(sql) { rs, _ -> rs.getIntId<RatkoPush>("id") }
        updatedPushes.forEach { pushId ->
            insertRatkoPushError(pushId, RatkoPushErrorType.INTERNAL, "Stuck operation cleared as FAILED")
        }
        logger.daoAccess(AccessType.UPDATE, RatkoPush::class, updatedPushes)
    }

    @Transactional
    fun updatePushStatus(pushId: IntId<RatkoPush>, status: RatkoPushStatus) {
        val sql =
            """
            update integrations.ratko_push
            set 
              end_time = case when :status = 'IN_PROGRESS_M_VALUES' then null else now() end,
              status = :status::integrations.ratko_push_status
            where id = :push_id
            """
                .trimIndent()

        val params = mapOf("push_id" to pushId.intValue, "status" to status.name)

        jdbcTemplate.setUser()
        check(jdbcTemplate.update(sql, params) == 1)
        logger.daoAccess(AccessType.UPDATE, RatkoPush::class, pushId)
    }

    fun fetchPreviousPush(): RatkoPush {
        val sql =
            """
            select
              id,
              start_time,
              end_time,
              status
            from integrations.ratko_push
            order by end_time desc
            limit 1
            """
                .trimIndent()

        return jdbcTemplate
            .query(sql) { rs, _ ->
                RatkoPush(
                    id = rs.getIntId("id"),
                    startTime = rs.getInstant("start_time"),
                    endTime = rs.getInstantOrNull("end_time"),
                    status = rs.getEnum("status"),
                )
            }
            .first()
            .also { logger.daoAccess(AccessType.FETCH, RatkoPush::class, it.id) }
    }

    private fun updatePushContent(pushId: IntId<RatkoPush>, publicationIds: List<IntId<Publication>>) {
        require(publicationIds.isNotEmpty())

        val sql =
            """
            insert into integrations.ratko_push_content
              values (:publication_id, :ratko_push_id)
            on conflict (publication_id) 
              do update set ratko_push_id = :ratko_push_id
            """
                .trimIndent()

        val params =
            publicationIds
                .map { id -> mapOf("ratko_push_id" to pushId.intValue, "publication_id" to id.intValue) }
                .toTypedArray()

        check(jdbcTemplate.batchUpdate(sql, params).isNotEmpty())

        logger.daoAccess(AccessType.UPSERT, RatkoPush::class, pushId, publicationIds)
    }

    @Transactional
    fun insertRatkoPushError(
        ratkoPushId: IntId<RatkoPush>,
        ratkoPushErrorType: RatkoPushErrorType,
        technicalMessage: String,
        ratkoStatus: String? = null,
        operation: RatkoOperation? = null,
        target: RatkoPushTarget<*>? = null,
    ): IntId<RatkoPushError> {
        // language=SQL
        val sql =
            """
            insert into integrations.ratko_push_error(
              ratko_push_id,
              track_number_oid,
              track_number_id,
              location_track_oid,
              location_track_id,
              switch_oid,
              switch_id,
              error_type,
              operation,
              ratko_response_code,
              technical_message
            )
            values(
              :ratko_push_id, 
              :track_number_oid, 
              (select id from layout.track_number_external_id where external_id = :track_number_oid), 
              :location_track_oid, 
              (select id from layout.location_track_external_id where external_id = :location_track_oid), 
              :switch_oid,
              (select id from layout.switch_external_id where external_id = :switch_oid), 
              :error_type::integrations.ratko_push_error_type, 
              :operation::integrations.ratko_push_error_operation,
              :ratko_response_code,
              :technical_message
            )
            returning id
            """
                .trimIndent()
        val params =
            mapOf(
                "ratko_push_id" to ratkoPushId.intValue,
                "error_type" to ratkoPushErrorType.name,
                "operation" to operation?.name,
                "track_number_oid" to (target as? RatkoPushTargetTrackNumber)?.oid,
                "location_track_oid" to (target as? RatkoPushTargetLocationTrack)?.oid,
                "switch_oid" to (target as? RatkoPushTargetSwitch)?.oid,
                "ratko_response_code" to ratkoStatus,
                "technical_message" to technicalMessage,
            )

        return jdbcTemplate
            .queryOne<IntId<RatkoPushError>>(sql, params, ratkoPushId.toString()) { rs, _ -> rs.getIntId("id") }
            .also { errorId -> logger.daoAccess(AccessType.INSERT, RatkoPushError::class, errorId) }
    }

    fun getLatestPushedPublicationMoment(layoutBranch: LayoutBranch): Instant {
        // language=SQL
        val sql =
            """
            select 
              coalesce(max(publication.publication_time), '2000-01-01 00:00:00'::timestamptz) as latest_publication_time
            from integrations.ratko_push
            inner join integrations.ratko_push_content on ratko_push_content.ratko_push_id = ratko_push.id
            inner join publication.publication on publication.id = ratko_push_content.publication_id
            where ratko_push.status = 'SUCCESSFUL' and publication.design_id is not distinct from :design_id
            """
                .trimIndent()

        return jdbcTemplate
            .queryOne(sql, mapOf("design_id" to layoutBranch.designId?.intValue)) { rs, _ ->
                rs.getInstant("latest_publication_time")
            }
            .also { logger.daoAccess(AccessType.FETCH, "${Publication::class}.publicationTime") }
    }

    fun getLatestPublicationMoment(): Instant {
        // language=SQL
        val sql =
            """
            select coalesce(max(publication.publication_time), now()) as latest_publication_time
            from publication.publication
            """
                .trimIndent()
        return jdbcTemplate
            .queryOne(sql) { rs, _ -> rs.getInstant("latest_publication_time") }
            .also { logger.daoAccess(AccessType.FETCH, "${Publication::class}.publicationTime") }
    }

    fun getRatkoPushChangeTime(): Instant {
        val sql =
            """
            select coalesce(
                greatest(
                    max(ratko_push.end_time), 
                    max(ratko_push.start_time)
                ), 
                now()
            ) as latest_ratko_push_time
            from integrations.ratko_push
            """
                .trimIndent()
        return jdbcTemplate
            .queryOne(sql) { rs, _ -> rs.getInstant("latest_ratko_push_time") }
            .also { logger.daoAccess(AccessType.FETCH, "${RatkoPush::class.simpleName}.changeTime") }
    }

    fun getRatkoStatuses(publicationIds: Set<IntId<Publication>>): Map<IntId<Publication>, List<RatkoPush>> {
        val sql =
            """
            select
              ratko_push_content.publication_id,
              ratko_push.id,
              ratko_push.start_time,
              ratko_push.end_time,
              ratko_push.status
            from integrations.ratko_push
            inner join integrations.ratko_push_content on ratko_push_content.ratko_push_id = ratko_push.id
            inner join publication.publication on publication.id = ratko_push_content.publication_id
            where publication.id = any(array[:publication_ids]::int[])
            """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("publication_ids" to publicationIds.map { it.intValue })) { rs, _ ->
                rs.getIntId<Publication>("publication_id") to
                    RatkoPush(
                        id = rs.getIntId("id"),
                        startTime = rs.getInstant("start_time"),
                        endTime = rs.getInstantOrNull("end_time"),
                        status = rs.getEnum("status"),
                    )
            }
            .groupBy({ it.first }, { it.second })
            .onEach { (_, pushes) -> logger.daoAccess(AccessType.FETCH, RatkoPush::class, pushes.map { it.id }) }
    }

    fun getCurrentRatkoPushError(): Pair<RatkoPushError, IntId<Publication>>? {
        val sql =
            """
            select 
              ratko_push_content.publication_id,
              ratko_push.status,
              ratko_push_error.id,
              ratko_push_error.error_type,
              ratko_push_error.operation,
              ratko_push_error.track_number_id,
              ratko_push_error.track_number_oid,
              ratko_push_error.location_track_id,
              ratko_push_error.location_track_oid,
              ratko_push_error.switch_id,
              ratko_push_error.switch_oid,
              ratko_push_error.ratko_push_id,
              ratko_push_error.ratko_response_code,
              ratko_push_error.technical_message
            from integrations.ratko_push
              inner join integrations.ratko_push_content on ratko_push_content.ratko_push_id = ratko_push.id
              left join integrations.ratko_push_error on ratko_push_error.ratko_push_id = ratko_push.id
            where status <> 'IN_PROGRESS' and status <> 'IN_PROGRESS_M_VALUES'
            order by start_time desc
            limit 1
            """
                .trimIndent()

        return jdbcTemplate
            .queryOptional(sql, emptyMap<String, Any>()) { rs, _ ->
                val status = rs.getEnum<RatkoPushStatus>("status")
                produceIf(status != RatkoPushStatus.SUCCESSFUL) { rs.getIntIdOrNull<RatkoPushError>("id") }
                    ?.let { errorId ->
                        val errorData =
                            RatkoErrorData(
                                id = errorId,
                                ratkoPushId = rs.getIntId("ratko_push_id"),
                                errorType = rs.getEnum("error_type"),
                                ratkoStatusCode = rs.getString("ratko_response_code"),
                                technicalMessage = rs.getString("technical_message"),
                            )
                        val pushOperation =
                            rs.getEnumOrNull<RatkoOperation>("operation")?.let { operation ->
                                toAssetRef(
                                        rs.getIntIdOrNull("track_number_id"),
                                        rs.getOidOrNull("track_number_oid"),
                                        rs.getIntIdOrNull("location_track_id"),
                                        rs.getOidOrNull("location_track_oid"),
                                        rs.getIntIdOrNull("switch_id"),
                                        rs.getOidOrNull("switch_oid"),
                                    )
                                    ?.let { operation to it }
                            }

                        val pushError =
                            when (pushOperation) {
                                null -> RatkoPushGeneralError(errorData)
                                else -> RatkoPushAssetError(pushOperation.first, errorData, pushOperation.second)
                            }

                        pushError to rs.getIntId<Publication>("publication_id")
                    }
            }
            .also { logger.daoAccess(AccessType.FETCH, RatkoPushError::class) }
    }

    private fun toAssetRef(
        trackNumberId: IntId<LayoutTrackNumber>?,
        trackNumberOid: Oid<LayoutTrackNumber>?,
        locationTrackId: IntId<LocationTrack>?,
        locationTrackOid: Oid<LocationTrack>?,
        switchId: IntId<LayoutSwitch>?,
        switchOid: Oid<LayoutSwitch>?,
    ): RatkoAssetRef<*>? =
        when {
            trackNumberId != null -> RatkoTrackNumberRef(trackNumberId, trackNumberOid)
            locationTrackId != null -> RatkoLocationTrackRef(locationTrackId, locationTrackOid)
            switchId != null -> RatkoSwitchRef(switchId, switchOid)
            else -> null
        }
}
