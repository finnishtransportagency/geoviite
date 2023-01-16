package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.integration.*
import fi.fta.geoviite.infra.logging.AccessType.FETCH
import fi.fta.geoviite.infra.logging.AccessType.INSERT
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.*
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant

@Transactional(readOnly = true)
@Component
class PublicationDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    fun fetchTrackNumberPublishCandidates(): List<TrackNumberPublishCandidate> {
        val sql = """
            select
                draft_track_number.official_id, 
                draft_track_number.number, 
                draft_track_number.change_time, 
                draft_track_number.change_user,
                layout.infer_operation_from_state_transition(
                  official_track_number.state, 
                  draft_track_number.state
                ) operation
            from layout.track_number_publication_view draft_track_number
              left join layout.track_number_publication_view official_track_number 
                on draft_track_number.official_id = official_track_number.official_id
                  and 'OFFICIAL' = any(official_track_number.publication_states)
            where draft_track_number.draft = true
        """.trimIndent()
        val candidates = jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, _ ->
            TrackNumberPublishCandidate(
                id = rs.getIntId("official_id"),
                number = rs.getTrackNumber("number"),
                draftChangeTime = rs.getInstant("change_time"),
                operation = rs.getEnum("operation"),
                userName = UserName(rs.getString("change_user"))
            )
        }
        logger.daoAccess(FETCH, TrackNumberPublishCandidate::class, candidates.map(TrackNumberPublishCandidate::id))
        return candidates
    }

    fun fetchReferenceLinePublishCandidates(): List<ReferenceLinePublishCandidate> {
        val sql = """
            select 
              coalesce(reference_line.draft_of_reference_line_id, reference_line.id) as official_id,
              reference_line.track_number_id, 
              reference_line.change_time,
              reference_line.change_user,
              draft_track_number.number as name, 
              layout.infer_operation_from_state_transition(
                track_number.state,
                draft_track_number.state
              ) as operation
            from layout.reference_line
              inner join layout.track_number on track_number.id = reference_line.track_number_id
              left join layout.track_number_publication_view draft_track_number 
                on draft_track_number.official_id = reference_line.track_number_id 
                  and 'DRAFT' = any(draft_track_number.publication_states)
            where reference_line.draft = true
        """.trimIndent()
        val candidates = jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, _ ->
            ReferenceLinePublishCandidate(
                id = rs.getIntId("official_id"),
                name = rs.getTrackNumber("name"),
                trackNumberId = rs.getIntId("track_number_id"),
                draftChangeTime = rs.getInstant("change_time"),
                userName = UserName(rs.getString("change_user")),
                operation = rs.getEnumOrNull<Operation>("operation") ?: Operation.MODIFY,
            )
        }
        logger.daoAccess(FETCH, ReferenceLinePublishCandidate::class, candidates.map(ReferenceLinePublishCandidate::id))
        return candidates
    }

    fun fetchLocationTrackPublishCandidates(): List<LocationTrackPublishCandidate> {
        val sql = """
            select 
                draft_location_track.official_id,
                draft_location_track.name, 
                draft_location_track.track_number_id, 
                draft_location_track.change_time, 
                draft_location_track.duplicate_of_location_track_id, 
                draft_location_track.change_user,
                layout.infer_operation_from_state_transition(
                  official_location_track.state, 
                  draft_location_track.state
                ) operation
            from layout.location_track_publication_view draft_location_track
              left join layout.location_track_publication_view official_location_track 
                on official_location_track.official_id = draft_location_track.official_id
                  and 'OFFICIAL' = any(official_location_track.publication_states)
            where draft_location_track.draft = true
        """.trimIndent()
        val candidates = jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, _ ->
            LocationTrackPublishCandidate(
                id = rs.getIntId("official_id"),
                name = AlignmentName(rs.getString("name")),
                trackNumberId = rs.getIntId("track_number_id"),
                draftChangeTime = rs.getInstant("change_time"),
                duplicateOf = rs.getIntIdOrNull("duplicate_of_location_track_id"),
                userName = UserName(rs.getString("change_user")),
                operation = rs.getEnum("operation"),
            )
        }
        logger.daoAccess(FETCH, LocationTrackPublishCandidate::class, candidates.map(LocationTrackPublishCandidate::id))
        return candidates
    }

    fun fetchSwitchPublishCandidates(): List<SwitchPublishCandidate> {
        val sql = """
            select 
            draft_switch.official_id,  
            draft_switch.name, 
            draft_switch.change_time,
            draft_switch.change_user,
            (select array_agg(sltn)
             from layout.switch_linked_track_numbers(coalesce(official_switch.row_id, draft_switch.row_id), :publication_state) sltn)
              as track_numbers,
            layout.infer_operation_from_state_category_transition(
              official_switch.state_category, 
              draft_switch.state_category
            ) operation
            from layout.switch_publication_view draft_switch
              left join layout.switch_publication_view official_switch 
                on draft_switch.official_id = official_switch.official_id
                  and 'OFFICIAL' = any(official_switch.publication_states)
            where draft_switch.draft = true
        """.trimIndent()
        val candidates = jdbcTemplate.query(sql, mapOf<String, Any>(
            "publication_state" to PublishType.DRAFT.name,
        )) { rs, _ ->
            SwitchPublishCandidate(
                id = rs.getIntId("official_id"),
                name = SwitchName(rs.getString("name")),
                draftChangeTime = rs.getInstant("change_time"),
                userName = UserName(rs.getString("change_user")),
                operation = rs.getEnum("operation"),
                trackNumberIds = rs.getIntIdArray("track_numbers"),
            )
        }
        logger.daoAccess(FETCH, SwitchPublishCandidate::class, candidates.map(SwitchPublishCandidate::id))
        return candidates
    }

    fun fetchKmPostPublishCandidates(): List<KmPostPublishCandidate> {
        val sql = """
            select
                draft_km_post.official_id, 
                draft_km_post.track_number_id, 
                draft_km_post.km_number, 
                draft_km_post.change_time, 
                draft_km_post.change_user,
                layout.infer_operation_from_state_transition(
                  official_km_post.state, 
                  draft_km_post.state
                ) operation
            from layout.km_post_publication_view draft_km_post
              left join layout.km_post_publication_view official_km_post 
                on draft_km_post.official_id = official_km_post.official_id
                  and 'OFFICIAL' = any(official_km_post.publication_states)
            where draft_km_post.draft = true
            order by km_number
        """.trimIndent()
        val candidates = jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, _ ->
            KmPostPublishCandidate(
                id = rs.getIntId("official_id"),
                trackNumberId = rs.getIntId("track_number_id"),
                kmNumber = rs.getKmNumber("km_number"),
                draftChangeTime = rs.getInstant("change_time"),
                userName = UserName(rs.getString("change_user")),
                operation = rs.getEnum("operation"),
            )
        }
        logger.daoAccess(FETCH, KmPostPublishCandidate::class, candidates.map(KmPostPublishCandidate::id))
        return candidates
    }
    
    @Transactional
    fun createPublication(
        trackNumbers: List<RowVersion<TrackLayoutTrackNumber>>,
        referenceLines: List<RowVersion<ReferenceLine>>,
        locationTracks: List<RowVersion<LocationTrack>>,
        switches: List<RowVersion<TrackLayoutSwitch>>,
        kmPosts: List<RowVersion<TrackLayoutKmPost>>,
    ): IntId<Publication> {
        jdbcTemplate.setUser()
        val sql = """
            insert into publication.publication(publication_user, publication_time)
            values (current_setting('geoviite.edit_user'), now())
            returning id
        """.trimIndent()
        val publicationId: IntId<Publication> = jdbcTemplate.queryForObject(sql, mapOf<String, Any>()) { rs, _ ->
            rs.getIntId("id")
        } ?: throw IllegalStateException("Failed to generate ID for new publish row")

        jdbcTemplate.batchUpdate(
            """insert into publication.track_number(publication_id, track_number_id, track_number_version) 
               values (:publication_id, :row_id, :row_version)
            """.trimIndent(),
            publishedRowParams(publicationId, trackNumbers),
        )
        jdbcTemplate.batchUpdate(
            """insert into publication.reference_line(publication_id, reference_line_id, reference_line_version) 
               values (:publication_id, :row_id, :row_version)
            """.trimIndent(),
            publishedRowParams(publicationId, referenceLines),
        )
        jdbcTemplate.batchUpdate(
            """insert into publication.location_track(publication_id, location_track_id, location_track_version) 
               values (:publication_id, :row_id, :row_version)
            """.trimIndent(),
            publishedRowParams(publicationId, locationTracks),
        )
        jdbcTemplate.batchUpdate(
            """insert into publication.switch(publication_id, switch_id, switch_version) 
               values (:publication_id, :row_id, :row_version)
            """.trimIndent(),
            publishedRowParams(publicationId, switches),
        )
        jdbcTemplate.batchUpdate(
            """insert into publication.km_post(publication_id, km_post_id, km_post_version) 
               values (:publication_id, :row_id, :row_version)
            """.trimIndent(),
            publishedRowParams(publicationId, kmPosts),
        )
        logger.daoAccess(INSERT, Publication::class, publicationId)
        return publicationId
    }

    fun fetchLinkedLocationTracks(
        switchId: IntId<TrackLayoutSwitch>,
        publicationStatus: PublishType,
    ): List<RowVersion<LocationTrack>> {
        val sql = """
            select 
              location_track.official_id,
              location_track.row_id,
              location_track.row_version,
              location_track.alignment_id,
              location_track.alignment_version 
            from layout.location_track_publication_view location_track
              left join layout.segment on segment.alignment_id = location_track.alignment_id
            where :publication_status = any(location_track.publication_states)
              and location_track.state != 'DELETED'
              and (
                location_track.topology_start_switch_id = :switch_id or
                location_track.topology_end_switch_id = :switch_id or
                segment.switch_id = :switch_id 
              )
            group by 
              location_track.official_id,
              location_track.row_id,
              location_track.row_version, 
              location_track.alignment_id, 
              location_track.alignment_version 
        """.trimIndent()
        val params = mapOf(
            "switch_id" to switchId.intValue,
            "publication_status" to publicationStatus.name,
        )
        return jdbcTemplate.query(sql, params) { rs, _ ->
            rs.getRowVersion("row_id", "row_version")
        }
    }

    fun getPublication(publicationId: IntId<Publication>): Publication {
        val sql = """
            select
              id,
              publication_user,
              publication_time
            from publication.publication
            where publication.id = :id
        """.trimIndent()

        return getOne(
            publicationId,
            jdbcTemplate.query(sql, mapOf("id" to publicationId.intValue)) { rs, _ ->
                Publication(
                    id = rs.getIntId("id"),
                    publicationUser = rs.getString("publication_user").let(::UserName),
                    publicationTime = rs.getInstant("publication_time")
                )
            }).also { logger.daoAccess(FETCH, Publication::class, publicationId) }
    }

    fun fetchCalculatedChangesInPublish(publicationId: IntId<Publication>): CalculatedChanges {
        val trackNumberChanges =
            fetchTrackNumberChangesInPublish(publicationId, fetchTrackNumberKmChangesInPublish(publicationId))
        val locationTrackChanges =
            fetchLocationTrackChangesInPublish(publicationId, fetchLocationTrackKmChangesInPublish(publicationId))
        val switchChanges = fetchSwitchChangesInPublish(publicationId, fetchSwitchJointChangesInPublish(publicationId))

        logger.daoAccess(FETCH, CalculatedChanges::class, publicationId)

        return CalculatedChanges(trackNumberChanges, locationTrackChanges, switchChanges)
    }

    @Transactional
    fun savePublishCalculatedChanges(publicationId: IntId<Publication>, changes: CalculatedChanges) {
        saveTrackNumberChangesInPublish(publicationId, changes.trackNumberChanges)
        saveLocationTrackChangesInPublish(publicationId, changes.locationTracksChanges)
        saveSwitchChangesInPublish(publicationId, changes.switchChanges)

        logger.daoAccess(INSERT, CalculatedChanges::class, publicationId)
    }

    //Inclusive from/start time, but exclusive to/end time
    fun fetchPublications(from: Instant?, to: Instant?): List<Publication> {
        val sql = """
            select id, publication_user, publication_time
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
                publicationTime = rs.getInstant("publication_time")
            )
        }.onEach { publication -> logger.daoAccess(FETCH, Publication::class, publication.id) }
    }

    private fun <T> publishedRowParams(publicationId: IntId<Publication>, rows: List<RowVersion<T>>) =
        rows.map { (rowId, rowVersion) ->
            mapOf(
                "publication_id" to publicationId.intValue,
                "row_id" to rowId.intValue,
                "row_version" to rowVersion,
            )
        }.toTypedArray()

    private fun fetchTrackNumberKmChangesInPublish(publicationId: IntId<Publication>) =
        jdbcTemplate.query(
            """
            select track_number_id, km_number
            from publication.calculated_change_to_track_number_km
            where publication_id = :publication_id
        """.trimIndent(), mapOf("publication_id" to publicationId.intValue)
        ) { rs, _ -> rs.getIntId<TrackLayoutTrackNumber>("track_number_id") to rs.getKmNumber("km_number") }
            .groupBy({ (tn, _) -> tn }, { (_, km) -> km })

    private fun fetchTrackNumberChangesInPublish(
        publicationId: IntId<Publication>,
        trackNumberKmChanges: Map<IntId<TrackLayoutTrackNumber>, List<KmNumber>>
    ) =
        jdbcTemplate.query(
            """
            select track_number_id, start_changed, end_changed
            from publication.calculated_change_to_track_number
            where publication_id = :publication_id
        """.trimIndent(), mapOf("publication_id" to publicationId.intValue)
        ) { rs, _ ->
            val id = rs.getIntId<TrackLayoutTrackNumber>("track_number_id")
            TrackNumberChange(
                id,
                trackNumberKmChanges[id]?.let(::HashSet) ?: setOf(),
                rs.getBoolean("start_changed"),
                rs.getBoolean("end_changed")
            )
        }

    private fun fetchLocationTrackKmChangesInPublish(publicationId: IntId<Publication>) =
        jdbcTemplate.query(
            """
            select location_track_id, km_number
            from publication.calculated_change_to_location_track_km
            where publication_id = :publication_id
        """.trimIndent(), mapOf("publication_id" to publicationId.intValue)
        ) { rs, _ -> rs.getIntId<LocationTrack>("location_track_id") to rs.getKmNumber("km_number") }
            .groupBy({ (lt, _) -> lt }, { (_, km) -> km })

    private fun fetchLocationTrackChangesInPublish(
        publicationId: IntId<Publication>,
        locationTrackKmChanges: Map<IntId<LocationTrack>, List<KmNumber>>
    ) =
        jdbcTemplate.query(
            """
            select location_track_id, start_changed, end_changed
            from publication.calculated_change_to_location_track
            where publication_id = :publication_id
        """.trimIndent(), mapOf("publication_id" to publicationId.intValue)
        ) { rs, _ ->
            val id = rs.getIntId<LocationTrack>("location_track_id")
            LocationTrackChange(
                id,
                locationTrackKmChanges[id]?.let(::HashSet) ?: setOf(),
                rs.getBoolean("start_changed"),
                rs.getBoolean("end_changed")
            )
        }

    private fun fetchSwitchJointChangesInPublish(publicationId: IntId<Publication>) =
        jdbcTemplate.query(
            """
            select switch_id, joint_number, removed, address,
                   postgis.st_x(point) as point_x, postgis.st_y(point) as point_y,
                   location_track_id, location_track_external_id, track_number_id, track_number_external_id
            from publication.calculated_change_to_switch_joint
            where publication_id = :publication_id
            """.trimIndent(),
            mapOf("publication_id" to publicationId.intValue)
        ) { rs, _ ->
            rs.getIntId<TrackLayoutSwitch>("switch_id") to SwitchJointChange(
                rs.getJointNumber("joint_number"),
                rs.getBoolean("removed"),
                rs.getTrackMeter("address"),
                rs.getPoint("point_x", "point_y"),
                rs.getIntId("location_track_id"),
                rs.getOid("location_track_external_id"),
                rs.getIntId("track_number_id"),
                rs.getOid("track_number_external_id")
            )
        }.groupBy({ (s, _) -> s }, { (_, js) -> js })

    private fun fetchSwitchChangesInPublish(
        publicationId: IntId<Publication>,
        switchJointChanges: Map<IntId<TrackLayoutSwitch>, List<SwitchJointChange>>
    ) =
        jdbcTemplate.query(
            """
            select switch_id
            from publication.calculated_change_to_switch
            where publication_id = :publication_id
            """.trimIndent(), mapOf("publication_id" to publicationId.intValue)
        ) { rs, _ ->
            val id = rs.getIntId<TrackLayoutSwitch>("switch_id")
            SwitchChange(
                id,
                switchJointChanges[id] ?: listOf(),
            )
        }

    private fun saveTrackNumberChangesInPublish(
        publicationId: IntId<Publication>,
        trackNumberChanges: List<TrackNumberChange>
    ) {
        jdbcTemplate.batchUpdate(
            """insert into publication.calculated_change_to_track_number
               values (:publication_id, :track_number_id, :start_changed, :end_changed)
            """.trimMargin(),
            trackNumberChanges.map { tnc ->
                mapOf(
                    "publication_id" to publicationId.intValue,
                    "track_number_id" to tnc.trackNumberId.intValue,
                    "start_changed" to tnc.isStartChanged,
                    "end_changed" to tnc.isEndChanged
                )
            }.toTypedArray()
        )

        jdbcTemplate.batchUpdate(
            """insert into publication.calculated_change_to_track_number_km
               values (:publication_id, :track_number_id, :km_number)
            """.trimMargin(),
            trackNumberChanges.flatMap { tnc ->
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

    private fun saveLocationTrackChangesInPublish(
        publicationId: IntId<Publication>,
        locationTrackChanges: List<LocationTrackChange>
    ) {
        jdbcTemplate.batchUpdate(
            """insert into publication.calculated_change_to_location_track
               values (:publication_id, :location_track_id, :start_changed, :end_changed)
            """.trimMargin(),
            locationTrackChanges.map { ltc ->
                mapOf(
                    "publication_id" to publicationId.intValue,
                    "location_track_id" to ltc.locationTrackId.intValue,
                    "start_changed" to ltc.isStartChanged,
                    "end_changed" to ltc.isEndChanged
                )
            }.toTypedArray()
        )
        jdbcTemplate.batchUpdate(
            """insert into publication.calculated_change_to_location_track_km
               values (:publication_id, :location_track_id, :km_number)
            """.trimMargin(),
            locationTrackChanges.flatMap { ltc ->
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

    private fun saveSwitchChangesInPublish(publicationId: IntId<Publication>, switchChanges: List<SwitchChange>) {
        jdbcTemplate.batchUpdate(
            """insert into publication.calculated_change_to_switch
               values (:publication_id, :switch_id)
            """.trimMargin(),
            switchChanges.map { s ->
                mapOf(
                    "publication_id" to publicationId.intValue,
                    "switch_id" to s.switchId.intValue
                )
            }.toTypedArray()
        )

        jdbcTemplate.batchUpdate(
            """insert into publication.calculated_change_to_switch_joint
               values (:publication_id, :switch_id, :joint_number, :removed, :address,
                      postgis.st_point(:point_x, :point_y),
                      :location_track_id, :location_track_external_id, :track_number_id, :track_number_external_id)
            """.trimMargin(),
            switchChanges.flatMap { s ->
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
                        "location_track_external_id" to cj.locationTrackExternalId.toString(),
                        "track_number_id" to cj.trackNumberId.intValue,
                        "track_number_external_id" to cj.trackNumberExternalId.toString()
                    )
                }
            }.toTypedArray()
        )
    }
}
