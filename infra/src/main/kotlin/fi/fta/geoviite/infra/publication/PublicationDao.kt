package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.configuration.CACHE_PUBLISHED_LOCATION_TRACKS
import fi.fta.geoviite.infra.configuration.CACHE_PUBLISHED_SWITCHES
import fi.fta.geoviite.infra.integration.*
import fi.fta.geoviite.infra.logging.AccessType.FETCH
import fi.fta.geoviite.infra.logging.AccessType.INSERT
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.*
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

@Transactional(readOnly = true)
@Component
class PublicationDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    val referenceLineDao: ReferenceLineDao,
) : DaoBase(jdbcTemplateParam) {

    fun fetchTrackNumberPublishCandidates(): List<TrackNumberPublishCandidate> {
        val sql = """
            select
              draft_track_number.row_id,
              draft_track_number.row_version,
              draft_track_number.official_id,
              draft_track_number.number,
              draft_track_number.change_time,
              draft_track_number.change_user,
              layout.infer_operation_from_state_transition(
                official_track_number.state,
                draft_track_number.state
              ) as operation
            from layout.track_number_publication_view draft_track_number
              left join layout.track_number_publication_view official_track_number 
                on draft_track_number.official_id = official_track_number.official_id
                  and 'OFFICIAL' = any(official_track_number.publication_states)
            where draft_track_number.draft = true
        """.trimIndent()
        val candidates = jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, _ ->
            val id = rs.getIntId<TrackLayoutTrackNumber>("official_id")
            TrackNumberPublishCandidate(
                id = id,
                rowVersion = rs.getRowVersion("row_id", "row_version"),
                number = rs.getTrackNumber("number"),
                draftChangeTime = rs.getInstant("change_time"),
                operation = rs.getEnum("operation"),
                userName = UserName(rs.getString("change_user")),
                boundingBox = referenceLineDao
                                .fetchVersion(PublishType.DRAFT, id)
                                ?.let(referenceLineDao::fetch)
                                ?.boundingBox
            )
        }
        logger.daoAccess(FETCH, TrackNumberPublishCandidate::class, candidates.map(TrackNumberPublishCandidate::id))
        return candidates
    }

    fun fetchReferenceLinePublishCandidates(): List<ReferenceLinePublishCandidate> {
        val sql = """
            select 
              draft_reference_line.row_id,
              draft_reference_line.row_version,
              draft_reference_line.official_id,
              draft_reference_line.track_number_id,
              draft_reference_line.change_time,
              draft_reference_line.change_user,
              draft_track_number.number as name,
              layout.infer_operation_from_state_transition(
                official_track_number.state,
                draft_track_number.state
              ) as operation,
              postgis.st_astext(alignment_version.bounding_box) as bounding_box
            from layout.reference_line_publication_view draft_reference_line
              left join layout.track_number_publication_view draft_track_number
                on draft_track_number.official_id = draft_reference_line.track_number_id
                  and 'DRAFT' = any(draft_track_number.publication_states)
              left join layout.track_number_publication_view official_track_number
                on official_track_number.official_id = draft_reference_line.track_number_id
                  and 'OFFICIAL' = any(official_track_number.publication_states)
              left join layout.alignment_version alignment_version
                on draft_reference_line.alignment_id = alignment_version.id
                  and draft_reference_line.alignment_version = alignment_version.version
            where draft_reference_line.draft = true
        """.trimIndent()
        val candidates = jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, _ ->
            ReferenceLinePublishCandidate(
                id = rs.getIntId("official_id"),
                rowVersion = rs.getRowVersion("row_id", "row_version"),
                name = rs.getTrackNumber("name"),
                trackNumberId = rs.getIntId("track_number_id"),
                draftChangeTime = rs.getInstant("change_time"),
                userName = UserName(rs.getString("change_user")),
                operation = rs.getEnum<Operation>("operation"),
                boundingBox = rs.getBboxOrNull("bounding_box"),
            )
        }
        logger.daoAccess(FETCH, ReferenceLinePublishCandidate::class, candidates.map(ReferenceLinePublishCandidate::id))
        return candidates
    }

    fun fetchLocationTrackPublishCandidates(): List<LocationTrackPublishCandidate> {
        val sql = """
            select 
              draft_location_track.row_id,
              draft_location_track.row_version,
              draft_location_track.official_id,
              draft_location_track.name,
              draft_location_track.track_number_id,
              draft_location_track.change_time,
              draft_location_track.duplicate_of_location_track_id,
              draft_location_track.change_user,
              layout.infer_operation_from_state_transition(
                official_location_track.state,
                draft_location_track.state
              ) as operation,
              postgis.st_astext(alignment_version.bounding_box) as bounding_box
            from layout.location_track_publication_view draft_location_track
              left join layout.location_track_publication_view official_location_track
                on official_location_track.official_id = draft_location_track.official_id
                  and 'OFFICIAL' = any(official_location_track.publication_states)
              left join layout.alignment_version alignment_version
                on draft_location_track.alignment_id = alignment_version.id
                  and draft_location_track.alignment_version = alignment_version.version
            where draft_location_track.draft = true
        """.trimIndent()
        val candidates = jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, _ ->
            LocationTrackPublishCandidate(
                id = rs.getIntId("official_id"),
                rowVersion = rs.getRowVersion("row_id", "row_version"),
                name = AlignmentName(rs.getString("name")),
                trackNumberId = rs.getIntId("track_number_id"),
                draftChangeTime = rs.getInstant("change_time"),
                duplicateOf = rs.getIntIdOrNull("duplicate_of_location_track_id"),
                userName = UserName(rs.getString("change_user")),
                operation = rs.getEnum("operation"),
                boundingBox = rs.getBboxOrNull("bounding_box")
            )
        }
        logger.daoAccess(FETCH, LocationTrackPublishCandidate::class, candidates.map(LocationTrackPublishCandidate::id))
        return candidates
    }

    fun fetchSwitchPublishCandidates(): List<SwitchPublishCandidate> {
        val sql = """
            select 
              draft_switch.row_id,
              draft_switch.row_version,
              draft_switch.official_id,
              draft_switch.name,
              draft_switch.change_time,
              draft_switch.change_user,
              (select array_agg(sltn)
                 from layout.switch_linked_track_numbers(coalesce(official_switch.row_id, draft_switch.row_id), :publication_state) sltn
              ) as track_numbers,
              layout.infer_operation_from_state_category_transition(
                official_switch.state_category,
                draft_switch.state_category
              ) as operation,
              postgis.st_x(switch_joint_version.location) as point_x, 
              postgis.st_y(switch_joint_version.location) as point_y
            from layout.switch_publication_view draft_switch
              left join layout.switch_publication_view official_switch
                on official_switch.official_id = draft_switch.official_id
                  and 'OFFICIAL' = any(official_switch.publication_states)
              left join common.switch_structure
                on draft_switch.switch_structure_id = switch_structure.id
              left join layout.switch_joint_version 
                on switch_joint_version.switch_id = coalesce(draft_switch.draft_id, draft_switch.official_id)
                  and switch_joint_version.switch_version = coalesce(draft_switch.draft_version, draft_switch.official_id)
                  and switch_joint_version.number = switch_structure.presentation_joint_number
            where draft_switch.draft = true
        """.trimIndent()
        val candidates = jdbcTemplate.query(sql, mapOf<String, Any>(
            "publication_state" to PublishType.DRAFT.name,
        )) { rs, _ ->
            SwitchPublishCandidate(
                id = rs.getIntId("official_id"),
                rowVersion = rs.getRowVersion("row_id", "row_version"),
                name = SwitchName(rs.getString("name")),
                draftChangeTime = rs.getInstant("change_time"),
                userName = UserName(rs.getString("change_user")),
                operation = rs.getEnum("operation"),
                trackNumberIds = rs.getIntIdArray("track_numbers"),
                location = rs.getPointOrNull("point_x", "point_y"),
            )
        }
        logger.daoAccess(FETCH, SwitchPublishCandidate::class, candidates.map(SwitchPublishCandidate::id))
        return candidates
    }

    fun fetchKmPostPublishCandidates(): List<KmPostPublishCandidate> {
        val sql = """
            select
              draft_km_post.row_id,
              draft_km_post.row_version,
              draft_km_post.official_id,
              draft_km_post.track_number_id,
              draft_km_post.km_number,
              draft_km_post.change_time,
              draft_km_post.change_user,
              layout.infer_operation_from_state_transition(
                official_km_post.state,
                draft_km_post.state
              ) as operation,
              postgis.st_x(draft_km_post.location) as point_x, 
              postgis.st_y(draft_km_post.location) as point_y
            from layout.km_post_publication_view draft_km_post
              left join layout.km_post_publication_view official_km_post
                on official_km_post.official_id = draft_km_post.official_id
                  and 'OFFICIAL' = any(official_km_post.publication_states)
            where draft_km_post.draft = true
            order by km_number
        """.trimIndent()
        val candidates = jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, _ ->
            KmPostPublishCandidate(
                id = rs.getIntId("official_id"),
                rowVersion = rs.getRowVersion("row_id", "row_version"),
                trackNumberId = rs.getIntId("track_number_id"),
                kmNumber = rs.getKmNumber("km_number"),
                draftChangeTime = rs.getInstant("change_time"),
                userName = UserName(rs.getString("change_user")),
                operation = rs.getEnum("operation"),
                location = rs.getPointOrNull("point_x", "point_y"),
            )
        }
        logger.daoAccess(FETCH, KmPostPublishCandidate::class, candidates.map(KmPostPublishCandidate::id))
        return candidates
    }

    @Transactional
    fun createPublication(message: String): IntId<Publication> {
        jdbcTemplate.setUser()
        val sql = """
            insert into publication.publication(publication_user, publication_time, message)
            values (current_setting('geoviite.edit_user'), now(), :message)
            returning id
        """.trimIndent()
        val publicationId: IntId<Publication> =
            jdbcTemplate.queryForObject(sql, mapOf<String, Any>("message" to message)) { rs, _ ->
                rs.getIntId("id")
            } ?: throw IllegalStateException("Failed to generate ID for new publish row")

        logger.daoAccess(INSERT, Publication::class, publicationId)
        return publicationId
    }

    fun fetchLinkedLocationTracks(
        switchIds: List<IntId<TrackLayoutSwitch>>,
        publicationStatus: PublishType,
    ): Map<IntId<TrackLayoutSwitch>, Set<RowVersion<LocationTrack>>> {
        if (switchIds.isEmpty()) return mapOf()
        val sql = """
            select
              lt.row_id,
              lt.row_version,
              array(select distinct v from unnest(
                array_agg(s.switch_id) || array_agg(lt.topology_start_switch_id) || array_agg(lt.topology_end_switch_id)
              ) as t(v) where v in (:switch_ids)) switch_ids
              from layout.location_track_publication_view lt
                left join layout.segment_version s on s.alignment_id = lt.alignment_id
                and s.alignment_version = lt.alignment_version
              where :publication_status = any(lt.publication_states)
                and lt.state != 'DELETED'
                and (
                    lt.topology_start_switch_id in (:switch_ids) or
                    lt.topology_end_switch_id in (:switch_ids) or
                    s.switch_id in (:switch_ids)
                )
              group by
                lt.row_id,
                lt.row_version
        """.trimIndent()
        val params = mapOf(
            "switch_ids" to switchIds.map(IntId<TrackLayoutSwitch>::intValue),
            "publication_status" to publicationStatus.name,
        )
        val result = mutableMapOf<IntId<TrackLayoutSwitch>, Set<RowVersion<LocationTrack>>>()
        jdbcTemplate.query(sql, params) { rs, _ ->
            val trackVersion = rs.getRowVersion<LocationTrack>("row_id", "row_version")
            val switchIdList = rs.getIntIdArray<TrackLayoutSwitch>("switch_ids")
            trackVersion to switchIdList
        }.forEach { (trackVersion, switchIdList) ->
            switchIdList.forEach { id -> result[id] = result.getOrElse(id, ::setOf) + trackVersion }
        }
        logger.daoAccess(FETCH, "switch_track_link", result)
        return result
    }

    fun getPublication(publicationId: IntId<Publication>): Publication {
        val sql = """
            select
              id,
              publication_user,
              publication_time,
              message
            from publication.publication
            where publication.id = :id
        """.trimIndent()

        return getOne(
            publicationId,
            jdbcTemplate.query(sql, mapOf("id" to publicationId.intValue)) { rs, _ ->
                Publication(
                    id = rs.getIntId("id"),
                    publicationUser = rs.getString("publication_user").let(::UserName),
                    publicationTime = rs.getInstant("publication_time"),
                    message = rs.getString("message")
                )
            }).also { logger.daoAccess(FETCH, Publication::class, publicationId) }
    }

    @Transactional
    fun insertCalculatedChanges(publicationId: IntId<Publication>, changes: CalculatedChanges) {
        saveTrackNumberChanges(
            publicationId,
            changes.directChanges.trackNumberChanges,
            changes.indirectChanges.trackNumberChanges
        )

        saveKmPostChanges(
            publicationId,
            changes.directChanges.kmPostChanges
        )

        saveReferenceLineChanges(
            publicationId,
            changes.directChanges.referenceLineChanges
        )

        saveLocationTrackChanges(
            publicationId,
            changes.directChanges.locationTrackChanges,
            changes.indirectChanges.locationTrackChanges
        )

        saveSwitchChanges(
            publicationId,
            changes.directChanges.switchChanges,
            changes.indirectChanges.switchChanges
        )

        logger.daoAccess(INSERT, CalculatedChanges::class, publicationId)
    }

    //Inclusive from/start time, but exclusive to/end time
    fun fetchPublicationsBetween(from: Instant?, to: Instant?): List<Publication> {
        val sql = """
            select id, publication_user, publication_time, message
            from publication.publication
            where (:from <= publication_time or :from::timestamptz is null) and (publication_time < :to or :to::timestamptz is null)
        """.trimIndent()

        val params = mapOf(
            "from" to from?.let { Timestamp.from(it) },
            "to" to to?.let { Timestamp.from(it) },
        )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            Publication(
                id = rs.getIntId("id"),
                publicationUser = rs.getString("publication_user").let(::UserName),
                publicationTime = rs.getInstant("publication_time"),
                message = rs.getString("message")
            )
        }.also { publications -> logger.daoAccess(FETCH, Publication::class, publications.map { it.id }) }
    }

    //Inclusive from/start time, but exclusive to/end time
    fun fetchLatestPublications(count: Int): List<Publication> {
        val sql = """
            select id, publication_user, publication_time, message
            from publication.publication
            order by id desc limit :count
        """.trimIndent()

        val params = mapOf(
            "count" to count
        )

        return jdbcTemplate.query(sql, params) { rs, _ ->
            Publication(
                id = rs.getIntId("id"),
                publicationUser = rs.getString("publication_user").let(::UserName),
                publicationTime = rs.getInstant("publication_time"),
                message = rs.getString("message")
            )
        }.also { publications -> logger.daoAccess(FETCH, Publication::class, publications.map { it.id }) }
    }

    fun fetchChangeTime(): Instant {
        val sql = "select max(publication_time) as publication_time from publication.publication"
        return jdbcTemplate.query(sql) {rs, _ -> rs.getInstantOrNull("publication_time")}
            .first() ?: Instant.ofEpochSecond(0)
    }

    private fun saveTrackNumberChanges(
        publicationId: IntId<Publication>,
        directChanges: Collection<TrackNumberChange>,
        indirectChanges: Collection<TrackNumberChange>,
    ) {
        jdbcTemplate.batchUpdate(
            """
                insert into publication.track_number (
                  publication_id,
                  track_number_id,
                  start_changed,
                  end_changed,
                  track_number_version,
                  direct_change
                ) 
                select publication.id, track_number.id, :start_changed, :end_changed, track_number.version, :direct_change
                from publication.publication
                  inner join layout.track_number_at(publication_time) track_number on track_number.id = :track_number_id
                where publication.id = :publication_id
            """.trimMargin(),
            mapOf(true to directChanges, false to indirectChanges)
                .flatMap { (directChange, changes) ->
                    changes.map { change ->
                        mapOf(
                            "publication_id" to publicationId.intValue,
                            "track_number_id" to change.trackNumberId.intValue,
                            "start_changed" to change.isStartChanged,
                            "end_changed" to change.isEndChanged,
                            "direct_change" to directChange
                        )
                    }
                }.toTypedArray()
        )

        jdbcTemplate.batchUpdate(
            """insert into publication.track_number_km (publication_id, track_number_id, km_number)
               values (:publication_id, :track_number_id, :km_number)
            """.trimMargin(),
            (directChanges + indirectChanges).flatMap { tnc ->
                tnc.changedKmNumbers.map { km ->
                    mapOf(
                        "publication_id" to publicationId.intValue,
                        "track_number_id" to tnc.trackNumberId.intValue,
                        "km_number" to km.toString()
                    )
                }
            }.toTypedArray()
        )
    }

    private fun saveKmPostChanges(
        publicationId: IntId<Publication>,
        kmPostIds: Collection<IntId<TrackLayoutKmPost>>,
    ) {
        jdbcTemplate.batchUpdate(
            """
                insert into publication.km_post (publication_id, km_post_id, km_post_version)
                select publication.id, km_post.id, km_post.version
                from publication.publication
                  inner join layout.km_post_at(publication_time) km_post on km_post.id = :km_post_id
                where publication.id = :publication_id
            """.trimMargin(),
            kmPostIds.map { id ->
                mapOf(
                    "publication_id" to publicationId.intValue,
                    "km_post_id" to id.intValue,
                )
            }.toTypedArray()
        )
    }

    private fun saveReferenceLineChanges(
        publicationId: IntId<Publication>,
        referenceLineIds: Collection<IntId<ReferenceLine>>,
    ) {
        jdbcTemplate.batchUpdate(
            """
                insert into publication.reference_line (publication_id, reference_line_id, reference_line_version)
                select publication.id, reference_line.id, reference_line.version
                from publication.publication
                  inner join layout.reference_line_at(publication_time) reference_line 
                    on reference_line.id = :reference_line_id 
                where publication.id = :publication_id
            """.trimMargin(),
            referenceLineIds.map { id ->
                mapOf(
                    "publication_id" to publicationId.intValue,
                    "reference_line_id" to id.intValue,
                )
            }.toTypedArray()
        )
    }

    private fun saveLocationTrackChanges(
        publicationId: IntId<Publication>,
        directChanges: Collection<LocationTrackChange>,
        indirectChanges: Collection<LocationTrackChange>,
    ) {
        jdbcTemplate.batchUpdate(
            """
                insert into publication.location_track (
                  publication_id,
                  location_track_id,
                  start_changed,
                  end_changed,
                  location_track_version,
                  direct_change
                )
                select publication.id, location_track.id, :start_changed, :end_changed, location_track.version, :direct_change
                from publication.publication
                  inner join layout.location_track_at(publication_time) location_track 
                    on location_track.id = :location_track_id
                where publication.id = :publication_id
            """.trimMargin(),
            mapOf(true to directChanges, false to indirectChanges)
                .flatMap { (directChange, changes) ->
                    changes.map { change ->
                        mapOf(
                            "publication_id" to publicationId.intValue,
                            "location_track_id" to change.locationTrackId.intValue,
                            "start_changed" to change.isStartChanged,
                            "end_changed" to change.isEndChanged,
                            "direct_change" to directChange
                        )
                    }
                }.toTypedArray()
        )

        jdbcTemplate.batchUpdate(
            """insert into publication.location_track_km (publication_id, location_track_id, km_number)
               values (:publication_id, :location_track_id, :km_number)
            """.trimMargin(),
            (directChanges + indirectChanges).flatMap { ltc ->
                ltc.changedKmNumbers.map { km ->
                    mapOf(
                        "publication_id" to publicationId.intValue,
                        "location_track_id" to ltc.locationTrackId.intValue,
                        "km_number" to km.toString()
                    )
                }
            }.toTypedArray()
        )
    }

    private fun saveSwitchChanges(
        publicationId: IntId<Publication>,
        directChanges: Collection<SwitchChange>,
        indirectChanges: Collection<SwitchChange>,
    ) {
        jdbcTemplate.batchUpdate(
            """
                insert into publication.switch (publication_id, switch_id, switch_version, direct_change) 
                select publication.id, switch.id, switch.version, :direct_change
                from publication.publication
                  inner join layout.switch_at(publication_time) switch on switch.id = :switch_id
                where publication.id = :publication_id
            """.trimMargin(),
            mapOf(true to directChanges, false to indirectChanges)
                .flatMap { (directChange, changes) ->
                    changes.map { change ->
                        mapOf(
                            "publication_id" to publicationId.intValue,
                            "switch_id" to change.switchId.intValue,
                            "direct_change" to directChange
                        )
                    }
                }.toTypedArray()
        )

        jdbcTemplate.batchUpdate(
            """
                insert into publication.switch_location_tracks (
                  publication_id,
                  switch_id,
                  location_track_id,
                  location_track_version,
                  is_topology_switch
                )
                select distinct :publication_id, :switch_id, location_track.id, location_track.version, false
                from layout.location_track
                 inner join layout.segment_version on segment_version.alignment_id = location_track.alignment_id
                   and location_track.alignment_version = segment_version.alignment_version
                where segment_version.switch_id = :switch_id 
                  and not location_track.draft
                union all
                select distinct :publication_id, :switch_id, location_track.id, location_track.version, true
                from layout.location_track
                where not location_track.draft 
                  and (location_track.topology_start_switch_id = :switch_id or location_track.topology_end_switch_id = :switch_id)
            """.trimIndent(),
            (directChanges + indirectChanges).map { s ->
                mapOf(
                    "publication_id" to publicationId.intValue,
                    "switch_id" to s.switchId.intValue
                )
            }.toTypedArray()
        )

        jdbcTemplate.batchUpdate(
            """
                insert into publication.switch_joint(
                  publication_id,
                  switch_id,
                  joint_number,
                  removed,
                  address,
                  point,
                  location_track_id,
                  location_track_external_id, 
                  track_number_id,
                  track_number_external_id)
                values (
                  :publication_id,
                  :switch_id,
                  :joint_number,
                  :removed,
                  :address,
                  postgis.st_point(:point_x, :point_y),
                  :location_track_id,
                  :location_track_external_id,
                  :track_number_id,
                  :track_number_external_id
                )
            """.trimMargin(),
            (directChanges + indirectChanges).flatMap { s ->
                s.changedJoints.map { cj ->
                    mapOf(
                        "publication_id" to publicationId.intValue,
                        "switch_id" to s.switchId.intValue,
                        "joint_number" to cj.number.intValue,
                        "removed" to cj.isRemoved,
                        "address" to cj.address.toString(),
                        "point_x" to cj.point.x,
                        "point_y" to cj.point.y,
                        "location_track_id" to cj.locationTrackId.intValue,
                        "location_track_external_id" to cj.locationTrackExternalId,
                        "track_number_id" to cj.trackNumberId.intValue,
                        "track_number_external_id" to cj.trackNumberExternalId
                    )
                }
            }.toTypedArray()
        )
    }

    @Cacheable(CACHE_PUBLISHED_LOCATION_TRACKS, sync = true)
    fun fetchPublishedLocationTracks(
        publicationId: IntId<Publication>,
    ): PublishedItemListing<PublishedLocationTrack> {
        val sql = """
            select
              plt.location_track_id as id,
              location_track_version as version,
              name,
              track_number_id,
              layout.infer_operation_from_state_transition(lt.old_state, lt.state) as operation,
              direct_change,
              array_remove(array_agg(pltk.km_number), null) as changed_km
            from publication.location_track plt
              inner join layout.location_track_change_view lt
                on lt.id = plt.location_track_id and lt.version = plt.location_track_version
              left join publication.location_track_km pltk
                on pltk.location_track_id = plt.location_track_id and pltk.publication_id = plt.publication_id
            where plt.publication_id = :publication_id
            group by plt.location_track_id, location_track_version, name, track_number_id, operation, direct_change;
        """.trimIndent()

        return jdbcTemplate.query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
            rs.getBoolean("direct_change") to PublishedLocationTrack(
                version = rs.getRowVersion("id", "version"),
                name = AlignmentName(rs.getString("name")),
                trackNumberId = rs.getIntId("track_number_id"),
                operation = rs.getEnum("operation"),
                changedKmNumbers = rs.getStringArrayOrNull("changed_km")?.map(::KmNumber)?.toSet() ?: emptySet()
            )
        }.let { locationTrackRows ->
            logger.daoAccess(FETCH, PublishedLocationTrack::class, locationTrackRows.map { it.second.version })
            partitionDirectIndirectChanges(locationTrackRows)
        }
    }

    fun fetchPublishedReferenceLines(publicationId: IntId<Publication>): List<PublishedReferenceLine> {
        val sql = """
            with prev_pub 
              as (select max(publication_time) as prev_publication_time 
              from publication.publication where id < :publication_id
            )
            select
              prl.reference_line_id as id,
              reference_line_version as version,
              rl.track_number_id,
              layout.infer_operation_from_state_transition(
                  tn_old.state,
                  tn.state
                ) as operation,
              array_remove(array_agg(ptnk.km_number), null) as changed_km
              from publication.reference_line prl
                left join prev_pub on true
                left join publication.publication p
                          on p.id = prl.publication_id
                inner join layout.reference_line_version rl
                          on rl.id = prl.reference_line_id and rl.version = prl.reference_line_version
                left join publication.track_number ptn
                          on ptn.track_number_id = rl.track_number_id and ptn.publication_id = prl.publication_id
                left join layout.track_number_change_view tn
                          on tn.id = ptn.track_number_id and tn.version = ptn.track_number_version
                left join layout.track_number_at(coalesce(prev_pub.prev_publication_time, p.publication_time - interval '00:00:00.001')) tn_old
                          on tn_old.id = ptn.track_number_id
                left join publication.track_number_km ptnk
                          on ptnk.track_number_id = ptn.track_number_id and ptnk.publication_id = ptn.publication_id
              where prl.publication_id = :publication_id
              group by prl.reference_line_id, reference_line_version, rl.track_number_id, operation;
        """.trimIndent()
        return jdbcTemplate.query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
            PublishedReferenceLine(
                version = rs.getRowVersion("id", "version"),
                trackNumberId = rs.getIntId("track_number_id"),
                operation = rs.getEnumOrNull<Operation>("operation") ?: Operation.MODIFY,
                changedKmNumbers = rs.getStringArray("changed_km").map(::KmNumber).toSet()
            )
        }.also { referenceLines ->
            logger.daoAccess(FETCH, PublishedReferenceLine::class, referenceLines.map { it.version })
        }
    }

    fun fetchPublishedKmPosts(publicationId: IntId<Publication>): List<PublishedKmPost> {
        val sql = """
            select
              pkp.km_post_id as id,
              km_post_version as version,
              layout.infer_operation_from_state_transition(old_state, state) as operation,
              km_number,
              track_number_id
            from publication.km_post pkp
              inner join layout.km_post_change_view kp
                on kp.id = pkp.km_post_id and kp.version = pkp.km_post_version
            where publication_id = :publication_id
        """.trimIndent()

        return jdbcTemplate.query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
            PublishedKmPost(
                version = rs.getRowVersion("id", "version"),
                trackNumberId = rs.getIntId("track_number_id"),
                kmNumber = rs.getKmNumber("km_number"),
                operation = rs.getEnum("operation")
            )
        }.also { kmPosts -> logger.daoAccess(FETCH, PublishedKmPost::class, kmPosts.map { it.version }) }
    }

    @Transactional(readOnly = true)
    @Cacheable(CACHE_PUBLISHED_SWITCHES, sync = true)
    fun fetchPublishedSwitches(publicationId: IntId<Publication>): PublishedItemListing<PublishedSwitch> {
        val sql = """
            select
              ps.switch_id as id,
              switch_version as version,
              switch.name,
              layout.infer_operation_from_state_category_transition(old_state_category, state_category) as operation,
              direct_change,
              array_remove(array_agg(distinct lt.track_number_id), null) as track_number_ids
            from publication.switch ps
              inner join layout.switch_change_view switch
                on switch.id = ps.switch_id and switch.version = ps.switch_version
              left join publication.switch_location_tracks slt 
                on slt.switch_id = ps.switch_id and slt.publication_id = ps.publication_id
              left join layout.location_track_version lt 
                on lt.id = slt.location_track_id and lt.version = slt.location_track_version
            where ps.publication_id = :publication_id
            group by ps.switch_id, ps.switch_version, switch.name, operation, direct_change
        """.trimIndent()

        val publishedSwitchJoints = publishedSwitchJoints(publicationId)

        return jdbcTemplate.query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
            rs.getBoolean("direct_change") to PublishedSwitch(
                version = rs.getRowVersion("id", "version"),
                name = SwitchName(rs.getString("name")),
                trackNumberIds = rs.getIntIdArray<TrackLayoutTrackNumber>("track_number_ids").toSet(),
                operation = rs.getEnum("operation"),
                changedJoints = publishedSwitchJoints
                    .filter { it.first == rs.getIntId<TrackLayoutSwitch>("id") }
                    .flatMap { it.second }
            )
        }.let { switchRows ->
            logger.daoAccess(FETCH, PublishedSwitch::class, switchRows.map { it.second.version })
            partitionDirectIndirectChanges(switchRows)
        }
    }

    private fun publishedSwitchJoints(
        publicationId: IntId<Publication>,
    ): List<Pair<IntId<TrackLayoutSwitch>, List<SwitchJointChange>>> {
        val sql = """
            select
              switch_id as id,
              joint_number,
              removed,
              address,
              postgis.st_x(point) as x,
              postgis.st_y(point) as y,
              location_track_id,
              location_track_external_id,
              track_number_id,
              track_number_external_id
            from publication.switch_joint
            where publication_id = :publication_id
        """.trimIndent()

        return jdbcTemplate.query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
            rs.getIntId<TrackLayoutSwitch>("id") to SwitchJointChange(
                number = rs.getJointNumber("joint_number"),
                isRemoved = rs.getBoolean("removed"),
                address = rs.getTrackMeter("address"),
                point = rs.getPoint("x", "y"),
                locationTrackId = rs.getIntId("location_track_id"),
                locationTrackExternalId = rs.getOidOrNull("location_track_external_id"),
                trackNumberId = rs.getIntId("track_number_id"),
                trackNumberExternalId = rs.getOidOrNull("track_number_external_id"),
            )
        }
            .groupBy { it.first }
            .map { (switchId, switchJoints) ->
                switchId to switchJoints.map { it.second }
            }
    }

    fun fetchPublishedTrackNumbers(
        publicationId: IntId<Publication>,
    ): PublishedItemListing<PublishedTrackNumber> {
        val sql = """
          select
            ptn.track_number_id as id,
            track_number_version as version,
            number,
            layout.infer_operation_from_state_transition(
              old_state,
              state
            ) as operation,
            direct_change,
            array_remove(array_agg(ptnk.km_number), null) as changed_km
          from publication.track_number ptn
            inner join layout.track_number_change_view tn
              on tn.id = ptn.track_number_id and tn.version = ptn.track_number_version
            left join publication.track_number_km ptnk 
              on ptnk.publication_id = ptn.publication_id and ptnk.track_number_id = ptn.track_number_id
          where ptn.publication_id = :publication_id
          group by ptn.track_number_id, track_number_version, number, operation, direct_change
        """.trimIndent()

        return jdbcTemplate.query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
            rs.getBoolean("direct_change") to PublishedTrackNumber(
                version = rs.getRowVersion("id", "version"),
                number = rs.getTrackNumber("number"),
                operation = rs.getEnum("operation"),
                changedKmNumbers = rs.getStringArray("changed_km").map(::KmNumber).toSet()
            )
        }.let { trackNumberRows ->
            logger.daoAccess(FETCH, PublishedTrackNumber::class, trackNumberRows.map { it.second.version })
            partitionDirectIndirectChanges(trackNumberRows)
        }
    }
}

private fun <T> partitionDirectIndirectChanges(rows: List<Pair<Boolean, T>>) = rows.partition { it.first }
    .let { (directChanges, indirectChanges) ->
        PublishedItemListing(
            directChanges = directChanges.map { it.second },
            indirectChanges = indirectChanges.map { it.second }
        )
    }
