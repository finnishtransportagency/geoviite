package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.configuration.CACHE_PUBLISHED_LOCATION_TRACKS
import fi.fta.geoviite.infra.configuration.CACHE_PUBLISHED_SWITCHES
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.integration.*
import fi.fta.geoviite.infra.logging.AccessType.*
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.switchLibrary.SwitchType
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.*
import java.sql.Timestamp
import java.time.Instant
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class PublicationDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    val referenceLineDao: ReferenceLineDao,
    val alignmentDao: LayoutAlignmentDao,
) : DaoBase(jdbcTemplateParam) {

    fun fetchTrackNumberPublicationCandidates(
        transition: LayoutContextTransition
    ): List<TrackNumberPublicationCandidate> {
        val sql =
            """
            select id, design_id, draft, version, number, change_time, change_user, cancelled,
              layout.infer_operation_from_state_transition(
                (select state
                 from layout.track_number_in_layout_context('OFFICIAL', null) tilc
                 where tilc.id = candidate_track_number.id),
                candidate_track_number.state
              ) as operation
            from layout.track_number candidate_track_number
            where draft = (:candidate_state = 'DRAFT') and design_id is not distinct from :candidate_design_id
              and not (design_id is not null and not draft and (cancelled or exists (
                select * from layout.track_number drafted_cancellation
                where drafted_cancellation.draft
                  and drafted_cancellation.design_id = candidate_track_number.design_id
                  and drafted_cancellation.id = candidate_track_number.id)))
        """
                .trimIndent()
        val candidates =
            jdbcTemplate.query(sql, publicationCandidateSqlArguments(transition)) { rs, _ ->
                TrackNumberPublicationCandidate(
                    rowVersion = rs.getLayoutRowVersion("id", "design_id", "draft", "version"),
                    number = rs.getTrackNumber("number"),
                    draftChangeTime = rs.getInstant("change_time"),
                    operation = rs.getEnum("operation"),
                    userName = UserName.of(rs.getString("change_user")),
                    cancelled = rs.getBoolean("cancelled"),
                    boundingBox =
                        referenceLineDao
                            .fetchVersionByTrackNumberId(transition.baseContext, rs.getIntId("id"))
                            ?.let(referenceLineDao::fetch)
                            ?.boundingBox,
                )
            }
        logger.daoAccess(
            FETCH,
            TrackNumberPublicationCandidate::class,
            candidates.map(TrackNumberPublicationCandidate::id),
        )
        return candidates
    }

    fun fetchReferenceLinePublicationCandidates(
        transition: LayoutContextTransition
    ): List<ReferenceLinePublicationCandidate> {
        val sql =
            """
            select 
              candidate_reference_line.id,
              candidate_reference_line.design_id,
              candidate_reference_line.draft,
              candidate_reference_line.version,
              candidate_reference_line.track_number_id,
              candidate_reference_line.change_time,
              candidate_reference_line.change_user,
              candidate_track_number.number as name,
              candidate_reference_line.cancelled,
              layout.infer_operation_from_state_transition(
                (select state
                 from layout.track_number_in_layout_context('OFFICIAL', null)
                 where id = track_number_id),
                candidate_track_number.state
              ) as operation,
              postgis.st_astext(alignment_version.bounding_box) as bounding_box
            from layout.reference_line candidate_reference_line
              left join lateral
                (
                select *
                from (
                  select * from layout.track_number same_context_tn
                  where same_context_tn.draft = (:candidate_state = 'DRAFT')
                    and same_context_tn.design_id is not distinct from :candidate_design_id
                    and same_context_tn.id = candidate_reference_line.track_number_id
                  union all
                  select *
                  from layout.track_number_in_layout_context(:candidate_state::layout.publication_state,
                                                             :candidate_design_id)) visible_tn
                  where visible_tn.id = candidate_reference_line.track_number_id
                limit 1
                ) candidate_track_number on (true)
              left join layout.alignment_version alignment_version
                on candidate_reference_line.alignment_id = alignment_version.id
                  and candidate_reference_line.alignment_version = alignment_version.version
            where candidate_reference_line.draft = (:candidate_state = 'DRAFT')
              and candidate_reference_line.design_id is not distinct from :candidate_design_id
              and not (candidate_reference_line.design_id is not null and not candidate_reference_line.draft
                         and (candidate_reference_line.cancelled or exists (
                           select * from layout.reference_line drafted_cancellation
                           where drafted_cancellation.draft
                             and drafted_cancellation.design_id = candidate_reference_line.design_id
                             and drafted_cancellation.id = candidate_reference_line.id)))
        """
                .trimIndent()
        val candidates =
            jdbcTemplate.query(sql, publicationCandidateSqlArguments(transition)) { rs, _ ->
                ReferenceLinePublicationCandidate(
                    rowVersion = rs.getLayoutRowVersion("id", "design_id", "draft", "version"),
                    name = rs.getTrackNumber("name"),
                    trackNumberId = rs.getIntId("track_number_id"),
                    draftChangeTime = rs.getInstant("change_time"),
                    userName = UserName.of(rs.getString("change_user")),
                    operation = rs.getEnum<Operation>("operation"),
                    cancelled = rs.getBoolean("cancelled"),
                    boundingBox = rs.getBboxOrNull("bounding_box"),
                    geometryChanges = emptyList(),
                )
            }
        logger.daoAccess(
            FETCH,
            ReferenceLinePublicationCandidate::class,
            candidates.map(ReferenceLinePublicationCandidate::id),
        )
        return candidates
    }

    fun fetchLocationTrackPublicationCandidates(
        transition: LayoutContextTransition
    ): List<LocationTrackPublicationCandidate> {
        val sql =
            """
            with splits as (
                select
                    split.id as split_id,
                    source.id as source_track_id,
                    array_agg(stlt.location_track_id) as target_track_ids,
                    array_agg(split_updated_duplicates.duplicate_location_track_id) as split_updated_duplicate_ids
                from publication.split
                    inner join layout.location_track_version source 
                        on source.id = split.source_location_track_id
                          and source.layout_context_id = split.layout_context_id
                          and source.version = split.source_location_track_version
                    inner join publication.split_target_location_track stlt on stlt.split_id = split.id
                    left join publication.split_updated_duplicate split_updated_duplicates 
                        on split_updated_duplicates.split_id = split.id
                where split.publication_id is null
                group by split.id, source.id
            )
            select 
              candidate_location_track.id,
              candidate_location_track.design_id,
              candidate_location_track.draft,
              candidate_location_track.version,
              candidate_location_track.name,
              candidate_location_track.track_number_id,
              candidate_location_track.change_time,
              candidate_location_track.duplicate_of_location_track_id,
              candidate_location_track.change_user,
              candidate_location_track.cancelled,
              layout.infer_operation_from_location_track_state_transition(
                (select state
                 from layout.location_track_in_layout_context('OFFICIAL', null) base_lt
                 where base_lt.id = candidate_location_track.id),
                candidate_location_track.state
              ) as operation,
              postgis.st_astext(alignment_version.bounding_box) as bounding_box,
              splits.split_id
            from layout.location_track candidate_location_track
                left join layout.alignment_version alignment_version
                    on candidate_location_track.alignment_id = alignment_version.id
                        and candidate_location_track.alignment_version = alignment_version.version
                left join splits 
                    on splits.source_track_id = candidate_location_track.id
                        or candidate_location_track.id = any(splits.target_track_ids)
                        or candidate_location_track.id = any(splits.split_updated_duplicate_ids)
            where candidate_location_track.draft = (:candidate_state = 'DRAFT')
              and candidate_location_track.design_id is not distinct from :candidate_design_id and
              not (candidate_location_track.design_id is not null and not candidate_location_track.draft
                   and (cancelled or exists (
                     select * from layout.location_track drafted_cancellation
                     where drafted_cancellation.draft
                       and drafted_cancellation.design_id = candidate_location_track.design_id
                       and drafted_cancellation.id = candidate_location_track.id)))
        """
                .trimIndent()
        val candidates =
            jdbcTemplate.query(sql, publicationCandidateSqlArguments(transition)) { rs, _ ->
                LocationTrackPublicationCandidate(
                    rowVersion = rs.getLayoutRowVersion("id", "design_id", "draft", "version"),
                    name = AlignmentName(rs.getString("name")),
                    trackNumberId = rs.getIntId("track_number_id"),
                    draftChangeTime = rs.getInstant("change_time"),
                    duplicateOf = rs.getIntIdOrNull("duplicate_of_location_track_id"),
                    userName = UserName.of(rs.getString("change_user")),
                    operation = rs.getEnum("operation"),
                    boundingBox = rs.getBboxOrNull("bounding_box"),
                    cancelled = rs.getBoolean("cancelled"),
                    publicationGroup = rs.getIntIdOrNull<Split>("split_id")?.let(::PublicationGroup),
                    geometryChanges = listOf(),
                )
            }
        logger.daoAccess(
            FETCH,
            LocationTrackPublicationCandidate::class,
            candidates.map(LocationTrackPublicationCandidate::id),
        )
        requireUniqueIds(candidates)
        return candidates
    }

    fun fetchSwitchPublicationCandidates(transition: LayoutContextTransition): List<SwitchPublicationCandidate> {
        val sql =
            """
            with splits as (
                select
                    split.id as split_id,
                    array_agg(split_relinked_switches.switch_id) as split_relinked_switch_ids
                from publication.split
                    inner join publication.split_relinked_switch split_relinked_switches 
                        on split_relinked_switches.split_id = split.id
                where split.publication_id is null
                group by split.id
            )
            select 
              candidate_switch.id,
              candidate_switch.design_id,
              candidate_switch.draft,
              candidate_switch.version,
              candidate_switch.name,
              candidate_switch.change_time,
              candidate_switch.change_user,
              (select array_agg(sltn)
                 from layout.switch_linked_track_numbers(candidate_switch.id,
                                                         :candidate_state::layout.publication_state,
                                                         :candidate_design_id) sltn
              ) as track_numbers,
              layout.infer_operation_from_state_category_transition(
                (select state_category
                 from layout.switch_in_layout_context('OFFICIAL', null) official_switch
                 where official_switch.id = candidate_switch.id),
                candidate_switch.state_category
              ) as operation,
              postgis.st_x(switch_joint_version.location) as point_x, 
              postgis.st_y(switch_joint_version.location) as point_y,
              candidate_switch.cancelled,
              splits.split_id
            from layout.switch candidate_switch
              left join common.switch_structure on candidate_switch.switch_structure_id = switch_structure.id
              left join layout.switch_joint_version
                on switch_joint_version.switch_id = candidate_switch.id
                  and switch_joint_version.switch_layout_context_id = candidate_switch.layout_context_id
                  and switch_joint_version.switch_version = candidate_switch.version
                  and switch_joint_version.number = switch_structure.presentation_joint_number
             left join splits on candidate_switch.id = any(splits.split_relinked_switch_ids)
            where candidate_switch.draft = (:candidate_state = 'DRAFT')
              and candidate_switch.design_id is not distinct from :candidate_design_id
              and not (candidate_switch.design_id is not null and not candidate_switch.draft
                       and (cancelled or exists (
                         select * from layout.track_number drafted_cancellation
                         where drafted_cancellation.draft
                           and drafted_cancellation.design_id = design_id
                           and drafted_cancellation.id = candidate_switch.id)))
        """
                .trimIndent()
        val candidates =
            jdbcTemplate.query(sql, publicationCandidateSqlArguments(transition)) { rs, _ ->
                SwitchPublicationCandidate(
                    rowVersion = rs.getLayoutRowVersion("id", "design_id", "draft", "version"),
                    name = SwitchName(rs.getString("name")),
                    draftChangeTime = rs.getInstant("change_time"),
                    userName = UserName.of(rs.getString("change_user")),
                    operation = rs.getEnum("operation"),
                    trackNumberIds = rs.getIntIdArray("track_numbers"),
                    location = rs.getPointOrNull("point_x", "point_y"),
                    cancelled = rs.getBoolean("cancelled"),
                    publicationGroup = rs.getIntIdOrNull<Split>("split_id")?.let(::PublicationGroup),
                )
            }
        logger.daoAccess(FETCH, SwitchPublicationCandidate::class, candidates.map(SwitchPublicationCandidate::id))
        requireUniqueIds(candidates)
        return candidates
    }

    fun fetchKmPostPublicationCandidates(transition: LayoutContextTransition): List<KmPostPublicationCandidate> {
        val sql =
            """
            select
              candidate_km_post.id,
              candidate_km_post.design_id,
              candidate_km_post.draft,
              candidate_km_post.version,
              candidate_km_post.track_number_id,
              candidate_km_post.km_number,
              candidate_km_post.change_time,
              candidate_km_post.change_user,
              layout.infer_operation_from_state_transition(
                (select state
                 from layout.km_post_in_layout_context('OFFICIAL', null)
                 where id = candidate_km_post.id),
                candidate_km_post.state
              ) as operation,
              candidate_km_post.cancelled,
              postgis.st_x(candidate_km_post.layout_location) as point_x,
              postgis.st_y(candidate_km_post.layout_location) as point_y
            from layout.km_post candidate_km_post
            where draft = (:candidate_state = 'DRAFT')
              and design_id is not distinct from :candidate_design_id
              and not (design_id is not null and not draft and (cancelled or exists (
                select * from layout.km_post drafted_cancellation
                where drafted_cancellation.draft
                  and drafted_cancellation.design_id = design_id
                  and drafted_cancellation.id = candidate_km_post.id)))
            order by km_number
        """
                .trimIndent()
        val candidates =
            jdbcTemplate.query(sql, publicationCandidateSqlArguments(transition)) { rs, _ ->
                KmPostPublicationCandidate(
                    rowVersion = rs.getLayoutRowVersion("id", "design_id", "draft", "version"),
                    trackNumberId = rs.getIntId("track_number_id"),
                    kmNumber = rs.getKmNumber("km_number"),
                    draftChangeTime = rs.getInstant("change_time"),
                    userName = UserName.of(rs.getString("change_user")),
                    operation = rs.getEnum("operation"),
                    cancelled = rs.getBoolean("cancelled"),
                    location = rs.getPointOrNull("point_x", "point_y"),
                )
            }
        logger.daoAccess(FETCH, KmPostPublicationCandidate::class, candidates.map(KmPostPublicationCandidate::id))
        return candidates
    }

    @Transactional
    fun createPublication(layoutBranch: LayoutBranch, message: FreeTextWithNewLines): IntId<Publication> {
        jdbcTemplate.setUser()
        val sql =
            """
            insert into publication.publication(publication_user, publication_time, message, design_id)
            values (current_setting('geoviite.edit_user'), now(), :message, :design_id)
            returning id
        """
                .trimIndent()
        val publicationId: IntId<Publication> =
            jdbcTemplate.queryForObject(
                sql,
                mapOf("message" to message, "design_id" to layoutBranch.designId?.intValue),
            ) { rs, _ ->
                rs.getIntId("id")
            } ?: error("Failed to generate ID for new publication row")

        logger.daoAccess(INSERT, Publication::class, publicationId)
        return publicationId
    }

    /**
     * @param switchIds Switches whose linked tracks to find.
     * @param locationTrackIdsInPublicationUnit Optionally specify the location tracks in the publication unit. Leave
     *   null to have all candidate location tracks considered in the publication unit.
     * @param includeDeleted Filters location tracks, not switches
     */
    fun fetchLinkedLocationTracks(
        target: ValidationTarget,
        switchIds: List<IntId<TrackLayoutSwitch>>,
        locationTrackIdsInPublicationUnit: List<IntId<LocationTrack>>? = null,
        includeDeleted: Boolean = false,
    ): Map<IntId<TrackLayoutSwitch>, Set<LayoutRowVersion<LocationTrack>>> {
        if (switchIds.isEmpty()) return mapOf()

        val candidateTrackIncludedCondition =
            if (locationTrackIdsInPublicationUnit == null) "true"
            else if (locationTrackIdsInPublicationUnit.isEmpty()) "false" else "lt.id in (:location_track_ids)"

        // language="sql"
        val sql =
            """
                with lt as not materialized (
                  select *
                    from layout.location_track_in_layout_context(:base_state::layout.publication_state,
                                                                 :base_design_id) lt
                    where not ($candidateTrackIncludedCondition)
                  union all
                  select *
                    from layout.location_track_in_layout_context(:candidate_state::layout.publication_state,
                                                                 :candidate_design_id) lt
                    where ($candidateTrackIncludedCondition)
                )
                select
                  lt.id,
                  lt.design_id,
                  lt.draft,
                  lt.version,
                  array_agg(distinct switch_id) as switch_ids
                  from (
                    select
                      lt.id,
                      lt.design_id,
                      lt.draft,
                      lt.version,
                      lt.state,
                      sv_switch.switch_id
                      from (
                        select distinct alignment_id, alignment_version, switch_id
                          from layout.segment_version
                          where switch_id in (:switch_ids)
                      ) sv_switch
                        join lt using (alignment_id, alignment_version)
                    union all
                    select id, design_id, draft, version, state, topology_start_switch_id
                      from lt
                      where topology_start_switch_id in (:switch_ids)
                    union all
                    select id, design_id, draft, version, state, topology_end_switch_id
                      from lt
                      where topology_end_switch_id in (:switch_ids)
                  ) lt
                  where (:include_deleted or lt.state != 'DELETED')
                  group by lt.id, lt.design_id, lt.draft, lt.version
            """
                .trimIndent()

        val params =
            mapOf(
                "switch_ids" to switchIds.map(IntId<TrackLayoutSwitch>::intValue),
                "location_track_ids" to locationTrackIdsInPublicationUnit?.map(IntId<*>::intValue),
                "include_deleted" to includeDeleted,
            ) + target.sqlParameters()
        val result = mutableMapOf<IntId<TrackLayoutSwitch>, Set<LayoutRowVersion<LocationTrack>>>()
        jdbcTemplate
            .query(sql, params) { rs, _ ->
                val trackVersion = rs.getLayoutRowVersion<LocationTrack>("id", "design_id", "draft", "version")
                val switchIdList = rs.getIntIdArray<TrackLayoutSwitch>("switch_ids")
                trackVersion to switchIdList
            }
            .forEach { (trackVersion, switchIdList) ->
                switchIdList.forEach { id -> result[id] = result.getOrElse(id, ::setOf) + trackVersion }
            }
        logger.daoAccess(FETCH, "switch_track_link", result)
        return result
    }

    fun fetchBaseDuplicateTrackVersions(
        baseContext: LayoutContext,
        ids: List<IntId<LocationTrack>>,
    ): Map<IntId<LocationTrack>, List<LayoutRowVersion<LocationTrack>>> {
        val sql =
            """
            select id, design_id, draft, version, duplicate_of_location_track_id
            from layout.location_track_in_layout_context(:publication_state::layout.publication_state, :design_id)
            where duplicate_of_location_track_id in (:ids)
              and state != 'DELETED'
        """
                .trimIndent()
        val params =
            mapOf(
                "ids" to ids.map { id -> id.intValue },
                "publication_state" to baseContext.state.name,
                "design_id" to baseContext.branch.designId?.intValue,
            )
        val rows =
            jdbcTemplate.query(sql, params) { rs, _ ->
                val duplicateOfId = rs.getIntId<LocationTrack>("duplicate_of_location_track_id")
                val version = rs.getLayoutRowVersion<LocationTrack>("id", "design_id", "draft", "version")
                duplicateOfId to version
            }
        return ids.associateWith { id ->
                rows.filter { (duplicateOfId, _) -> duplicateOfId == id }.map { (_, rv) -> rv }
            }
            .also { logger.daoAccess(FETCH, "Duplicate track versions", ids) }
    }

    fun getPublication(publicationId: IntId<Publication>): Publication {
        val sql =
            """
            select
              id,
              publication_user,
              publication_time,
              message,
              design_id
            from publication.publication
            where publication.id = :id
        """
                .trimIndent()

        return getOne(
                publicationId,
                jdbcTemplate.query(sql, mapOf("id" to publicationId.intValue)) { rs, _ ->
                    Publication(
                        id = rs.getIntId("id"),
                        publicationUser = rs.getString("publication_user").let(UserName::of),
                        publicationTime = rs.getInstant("publication_time"),
                        message = rs.getFreeTextWithNewLines("message"),
                        layoutBranch = rs.getLayoutBranch("design_id"),
                    )
                },
            )
            .also { logger.daoAccess(FETCH, Publication::class, publicationId) }
    }

    @Transactional
    fun insertCalculatedChanges(publicationId: IntId<Publication>, changes: CalculatedChanges) {
        saveTrackNumberChanges(
            publicationId,
            changes.directChanges.trackNumberChanges,
            changes.indirectChanges.trackNumberChanges,
        )

        saveKmPostChanges(publicationId, changes.directChanges.kmPostChanges)

        saveReferenceLineChanges(publicationId, changes.directChanges.referenceLineChanges)

        saveLocationTrackChanges(
            publicationId,
            changes.directChanges.locationTrackChanges,
            changes.indirectChanges.locationTrackChanges,
        )

        saveSwitchChanges(publicationId, changes.directChanges.switchChanges, changes.indirectChanges.switchChanges)

        logger.daoAccess(INSERT, CalculatedChanges::class, publicationId)
    }

    // Inclusive from/start time, but exclusive to/end time
    fun fetchPublicationsBetween(layoutBranch: LayoutBranch, from: Instant?, to: Instant?): List<Publication> {
        val sql =
            """
            select id, publication_user, publication_time, message, design_id
            from publication.publication
            where (:from <= publication_time or :from::timestamptz is null) and (publication_time < :to or :to::timestamptz is null)
              and design_id is not distinct from :design_id
            order by id
        """
                .trimIndent()

        val params =
            mapOf(
                "from" to from?.let { Timestamp.from(it) },
                "to" to to?.let { Timestamp.from(it) },
                "design_id" to layoutBranch.designId?.intValue,
            )

        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                Publication(
                    id = rs.getIntId("id"),
                    publicationUser = rs.getString("publication_user").let(UserName::of),
                    publicationTime = rs.getInstant("publication_time"),
                    message = rs.getFreeTextWithNewLines("message"),
                    layoutBranch = rs.getLayoutBranch("design_id"),
                )
            }
            .also { publications -> logger.daoAccess(FETCH, Publication::class, publications.map { it.id }) }
    }

    fun list(branchType: LayoutBranchType): List<Publication> {
        val sql =
            """
            select id, publication_user, publication_time, message, design_id
            from publication.publication
            where case when :branch_type = 'MAIN' then design_id is null else design_id is not null end
            order by id desc
        """
                .trimIndent()

        val params = mapOf("branch_type" to branchType.name)

        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                Publication(
                    id = rs.getIntId("id"),
                    publicationUser = rs.getString("publication_user").let(UserName::of),
                    publicationTime = rs.getInstant("publication_time"),
                    message = rs.getFreeTextWithNewLines("message"),
                    layoutBranch = rs.getLayoutBranch("design_id"),
                )
            }
            .also { publications -> logger.daoAccess(FETCH, Publication::class, publications.map { it.id }) }
    }

    fun fetchLatestPublications(branchType: LayoutBranchType, count: Int): List<Publication> {
        val sql =
            """
            select id, publication_user, publication_time, message, design_id
            from publication.publication
            where case when :branch_type = 'MAIN' then design_id is null else design_id is not null end
            order by id desc limit :count
        """
                .trimIndent()

        val params = mapOf("count" to count, "branch_type" to branchType.name)

        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                Publication(
                    id = rs.getIntId("id"),
                    publicationUser = rs.getString("publication_user").let(UserName::of),
                    publicationTime = rs.getInstant("publication_time"),
                    message = rs.getFreeTextWithNewLines("message"),
                    layoutBranch = rs.getLayoutBranch("design_id"),
                )
            }
            .also { publications -> logger.daoAccess(FETCH, Publication::class, publications.map { it.id }) }
    }

    fun fetchPublicationTimes(layoutBranch: LayoutBranch): Map<Instant, IntId<Publication>> {
        val sql =
            """
            select id, publication_time
            from publication.publication
            where design_id is not distinct from :design_id
        """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("design_id" to layoutBranch.designId?.intValue)) { rs, _ ->
                rs.getInstant("publication_time") to rs.getIntId<Publication>("id")
            }
            .toMap()
            .also { logger.daoAccess(FETCH, Publication::class, it.keys) }
    }

    fun fetchPublicationTrackNumberChanges(
        layoutBranch: LayoutBranch,
        publicationId: IntId<Publication>,
        comparisonTime: Instant,
    ): Map<IntId<TrackLayoutTrackNumber>, TrackNumberChanges> {
        assertMainBranch(layoutBranch)
        val sql =
            """
            select
              tn.id as tn_id,
              tn.number as track_number,
              old_tn.number as old_track_number,
              tn.description as description,
              old_tn.description as old_description,
              tn.state as state,
              old_tn.state as old_state,
              rl.start_address as start_address,
              old_rl.start_address as old_start_address,
              postgis.st_x(postgis.st_endpoint(old_sg_last.geometry)) as old_end_x,
              postgis.st_y(postgis.st_endpoint(old_sg_last.geometry)) as old_end_y,
              postgis.st_x(postgis.st_endpoint(sg_last.geometry)) as end_x,
              postgis.st_y(postgis.st_endpoint(sg_last.geometry)) as end_y
            from publication.track_number
              left join layout.track_number_version tn
                on tn.id = track_number.track_number_id
                  and tn.version = track_number_version
                  and tn.layout_context_id = track_number.layout_context_id
              left join publication.publication p on p.id = publication_id
              left join lateral
                (select * from layout.reference_line_version rl
                 where rl.track_number_id = tn.id and not rl.draft and rl.design_id is null
                   and rl.change_time <= p.publication_time
                 order by change_time desc
                 limit 1) rl on (true)
              left join layout.alignment_version av on av.id = rl.alignment_id and av.version = rl.alignment_version
              left join layout.segment_version sv_last on av.id = sv_last.alignment_id and av.version = sv_last.alignment_version and sv_last.segment_index = av.segment_count - 1
              left join layout.segment_geometry sg_last on sv_last.geometry_id = sg_last.id
              left join layout.track_number_version old_tn 
                on old_tn.id = track_number.track_number_id 
                  and ((track_number.direct_change = true and old_tn.version = track_number_version - 1) 
                    or (track_number.direct_change = false and old_tn.version = track_number_version)) 
                  and old_tn.draft = false and old_tn.design_id is null
              left join lateral (
                select * from layout.reference_line_version rl
                 where rl.track_number_id = tn.id and not rl.draft and rl.design_id is null
                   and rl.change_time <= :comparison_time
                 order by change_time desc
                 limit 1) old_rl on (true)
              left join layout.alignment_version old_av on old_av.id = old_rl.alignment_id and old_av.version = old_rl.alignment_version
              left join layout.segment_version old_sv_first on old_av.id = old_sv_first.alignment_id and old_av.version = old_sv_first.alignment_version and old_sv_first.segment_index = 0
              left join layout.segment_geometry old_sg_first on old_sv_first.geometry_id = old_sg_first.id
              left join layout.segment_version old_sv_last on old_av.id = old_sv_last.alignment_id and old_av.version = old_sv_last.alignment_version and old_sv_last.segment_index = old_av.segment_count - 1
              left join layout.segment_geometry old_sg_last on old_sv_last.geometry_id = old_sg_last.id
            where publication_id = :publication_id
        """
                .trimIndent()

        val params =
            mapOf("publication_id" to publicationId.intValue, "comparison_time" to Timestamp.from(comparisonTime))
        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                val id = rs.getIntId<TrackLayoutTrackNumber>("tn_id")
                id to
                    TrackNumberChanges(
                        id,
                        trackNumber = rs.getChange("track_number", rs::getTrackNumberOrNull),
                        description = rs.getChange("description") { rs.getString(it)?.let(::TrackNumberDescription) },
                        state = rs.getChange("state") { rs.getEnumOrNull<LayoutState>(it) },
                        startAddress = rs.getChange("start_address", rs::getTrackMeterOrNull),
                        endPoint = rs.getChangePoint("end_x", "end_y"),
                    )
            }
            .toMap()
            .also { logger.daoAccess(FETCH, TrackNumberChanges::class, publicationId) }
    }

    fun fetchPublicationLocationTrackSwitchLinkChanges(
        publicationId: IntId<Publication>
    ): Map<IntId<LocationTrack>, LocationTrackPublicationSwitchLinkChanges> =
        fetchPublicationLocationTrackSwitchLinkChanges(publicationId, null, null, null)[publicationId] ?: mapOf()

    fun fetchPublicationLocationTrackSwitchLinkChanges(
        publicationId: IntId<Publication>?,
        layoutBranch: LayoutBranch?,
        from: Instant?,
        to: Instant?,
    ): Map<IntId<Publication>, Map<IntId<LocationTrack>, LocationTrackPublicationSwitchLinkChanges>> {
        require((layoutBranch != null) != (publicationId != null)) {
            """"Must provide exactly one of layoutBranch or publicationId, but provided:
                |layoutBranch=$layoutBranch, publicationId=$publicationId"""
                .trimMargin()
        }
        val sql =
            """
            select
              change_side,
              plt.publication_id,
              location_track_id,
              switch_version.id as switch_id,
              switch_version.name as switch_name,
              switch_version.external_id as switch_oid
              from publication.publication
                join publication.location_track plt on publication.id = plt.publication_id
                join lateral
                (select 'new' as change_side, topology_start_switch_id, topology_end_switch_id, alignment_id, alignment_version
                   from layout.location_track_version ltv
                   where plt.location_track_id = ltv.id
                     and plt.layout_context_id = ltv.layout_context_id
                     and plt.location_track_version = ltv.version
                 union all
                 select 'old', topology_start_switch_id, topology_end_switch_id, alignment_id, alignment_version
                   from layout.location_track_version ltv
                   where plt.location_track_id = ltv.id
                     and plt.layout_context_id = ltv.layout_context_id
                     and plt.location_track_version = ltv.version + 1
                     and not ltv.draft) ltvs on (true)
                join lateral
                (select distinct switch_id
                   from (
                     select ltvs.topology_start_switch_id as switch_id
                     union all
                     select ltvs.topology_end_switch_id
                     union all
                     select switch_id
                       from layout.segment_version
                       where segment_version.alignment_id = ltvs.alignment_id
                         and segment_version.alignment_version = ltvs.alignment_version
                   ) s
                   where switch_id is not null) switch_ids on (true)
                join layout.switch_version on switch_ids.switch_id = switch_version.id and not switch_version.draft
                  and switch_version.design_id is null
              where direct_change
                and not exists(
                  select *
                  from publication.switch psw
                    join publication.publication psw_publication on psw.publication_id = psw_publication.id
                  where psw.switch_id = switch_version.id
                    and psw.layout_context_id = switch_version.layout_context_id
                    and psw_publication.design_id is not distinct from publication.design_id
                    and direct_change
                    and (psw.switch_version = switch_version.version and psw.publication_id > plt.publication_id
                      or psw.switch_version > switch_version.version and psw.publication_id <= plt.publication_id))
                and case when :publicationId::integer is not null
                      then :publicationId = publication.id
                      else :design_id is not distinct from publication.design_id end
                and (:from::timestamptz is null or :from <= publication_time)
                and (:to::timestamptz is null or :to >= publication_time)
        """
                .trimIndent()

        data class ResultRow(
            val changeSide: String,
            val publicationId: IntId<Publication>,
            val locationTrackId: IntId<LocationTrack>,
            val switchId: IntId<TrackLayoutSwitch>,
            val switchName: String,
            val switchOid: Oid<TrackLayoutSwitch>?,
        )

        return jdbcTemplate
            .query(
                sql,
                mapOf(
                    "publicationId" to publicationId?.intValue,
                    "from" to from?.let { Timestamp.from(it) },
                    "to" to to?.let { Timestamp.from(it) },
                    "design_id" to layoutBranch?.designId?.intValue,
                ),
            ) { rs, _ ->
                ResultRow(
                    rs.getString("change_side"),
                    rs.getIntId("publication_id"),
                    rs.getIntId("location_track_id"),
                    rs.getIntId("switch_id"),
                    rs.getString("switch_name"),
                    rs.getOidOrNull("switch_oid"),
                )
            }
            .groupBy { it.publicationId }
            .mapValues { (_, publicationResults) ->
                publicationResults
                    .groupBy { it.locationTrackId }
                    .mapValues { (_, locationTrackResults) ->
                        val (olds, news) = locationTrackResults.partition { it.changeSide == "old" }
                        LocationTrackPublicationSwitchLinkChanges(
                            old = olds.associateBy({ it.switchId }, { SwitchChangeIds(it.switchName, it.switchOid) }),
                            new = news.associateBy({ it.switchId }, { SwitchChangeIds(it.switchName, it.switchOid) }),
                        )
                    }
            }
    }

    fun fetchPublicationLocationTrackChanges(
        publicationId: IntId<Publication>
    ): Map<IntId<LocationTrack>, LocationTrackChanges> {
        val sql =
            """
            select
              location_track_id,
              location_track_version,
              ltv.name,
              old_ltv.name as old_name,
              ltv.description_base,
              old_ltv.description_base as old_description_base,
              ltv.description_suffix,
              old_ltv.description_suffix as old_description_suffix,
              ltv.duplicate_of_location_track_id,
              old_ltv.duplicate_of_location_track_id as old_duplicate_of_location_track_id,
              ltv.state,
              old_ltv.state as old_state,
              ltv.type,
              old_ltv.type as old_type,
              av.length,
              old_av.length as old_length,
              ltv.track_number_id as track_number_id,
              old_ltv.track_number_id as old_track_number_id,
              ltv.alignment_id,
              old_ltv.alignment_id as old_alignment_id,
              ltv.alignment_version,
              old_ltv.alignment_version as old_alignment_version,
              ltv.topology_start_switch_id,
              old_ltv.topology_start_switch_id as old_topology_start_switch_id,
              ltv.topology_end_switch_id,
              old_ltv.topology_end_switch_id as old_topology_end_switch_id,
              ltv.owner_id,
              old_ltv.owner_id as old_owner_id,
              postgis.st_x(postgis.st_startpoint(old_sg_first.geometry)) as old_start_x,
              postgis.st_y(postgis.st_startpoint(old_sg_first.geometry)) as old_start_y,
              postgis.st_x(postgis.st_endpoint(old_sg_last.geometry)) as old_end_x,
              postgis.st_y(postgis.st_endpoint(old_sg_last.geometry)) as old_end_y,
              postgis.st_x(postgis.st_startpoint(sg_first.geometry)) as start_x,
              postgis.st_y(postgis.st_startpoint(sg_first.geometry)) as start_y,
              postgis.st_x(postgis.st_endpoint(sg_last.geometry)) as end_x,
              postgis.st_y(postgis.st_endpoint(sg_last.geometry)) as end_y,
              geometry_change_summary_computed
              from publication.location_track
                join publication.publication on location_track.publication_id = publication.id
                left join layout.location_track_version ltv
                          on location_track.location_track_id = ltv.id
                            and location_track.location_track_version = ltv.version
                            and location_track.layout_context_id = ltv.layout_context_id
                left join layout.alignment_version av
                          on ltv.alignment_id = av.id and ltv.alignment_version = av.version
                left join layout.segment_version sv_first
                          on av.id = sv_first.alignment_id and av.version = sv_first.alignment_version and sv_first.segment_index = 0
                left join layout.segment_version sv_last
                          on av.id = sv_last.alignment_id and av.version = sv_last.alignment_version and sv_last.segment_index = av.segment_count - 1
                left join layout.segment_geometry sg_first
                          on sv_first.geometry_id = sg_first.id
                left join layout.segment_geometry sg_last
                          on sv_last.geometry_id = sg_last.id
                left join layout.location_track_version old_ltv
                          on old_ltv.id = ltv.id 
                            and ((old_ltv.version = ltv.version - 1 
                              and location_track.direct_change = true) 
                              or (old_ltv.version = ltv.version
                              and location_track.direct_change = false))
                            and old_ltv.draft = false
                            and old_ltv.design_id is null
                left join layout.alignment_version old_av
                          on old_ltv.alignment_id = old_av.id and old_ltv.alignment_version = old_av.version
                left join layout.segment_version old_sv_first
                          on old_av.id = old_sv_first.alignment_id and old_av.version = old_sv_first.alignment_version and old_sv_first.segment_index = 0
                left join layout.segment_version old_sv_last
                          on old_av.id = old_sv_last.alignment_id and old_av.version = old_sv_last.alignment_version and old_sv_last.segment_index = old_av.segment_count - 1
                left join layout.segment_geometry old_sg_first
                          on old_sv_first.geometry_id = old_sg_first.id
                left join layout.segment_geometry old_sg_last
                          on old_sv_last.geometry_id = old_sg_last.id
            where publication_id = :publication_id
        """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
                val id = rs.getIntId<LocationTrack>("location_track_id")
                id to
                    LocationTrackChanges(
                        id,
                        trackNumberId = rs.getChange("track_number_id", rs::getIntIdOrNull),
                        name = rs.getChange("name") { rs.getString(it)?.let(::AlignmentName) },
                        descriptionBase =
                            rs.getChange("description_base") { rs.getString(it)?.let(::LocationTrackDescriptionBase) },
                        descriptionSuffix =
                            rs.getChange("description_suffix") { rs.getEnumOrNull<LocationTrackDescriptionSuffix>(it) },
                        endPoint = rs.getChangePoint("end_x", "end_y"),
                        startPoint = rs.getChangePoint("start_x", "start_y"),
                        state = rs.getChange("state") { rs.getEnumOrNull<LocationTrackState>(it) },
                        duplicateOf = rs.getChange("duplicate_of_location_track_id", rs::getIntIdOrNull),
                        type = rs.getChange("type") { rs.getEnumOrNull<LocationTrackType>(it) },
                        length = rs.getChange("length", rs::getDoubleOrNull),
                        alignmentVersion = rs.getChangeRowVersion<LayoutAlignment>("alignment_id", "alignment_version"),
                        geometryChangeSummaries =
                            if (!rs.getBoolean("geometry_change_summary_computed")) null
                            else fetchGeometryChangeSummaries(publicationId, rs.getIntId("location_track_id")),
                        owner = rs.getChange("owner_id", rs::getIntIdOrNull),
                    )
            }
            .toMap()
            .also { logger.daoAccess(FETCH, LocationTrackChanges::class, publicationId) }
    }

    fun fetchGeometryChangeSummaries(
        publicationId: IntId<Publication>,
        locationTrackId: IntId<LocationTrack>,
    ): List<GeometryChangeSummary> {
        val sql =
            """
            select changed_length_m, max_distance, start_km, end_km, start_km_m, end_km_m
            from publication.location_track_geometry_change_summary
            where publication_id = :publicationId and location_track_id = :locationTrackId
            order by remark_order
        """
                .trimIndent()
        return jdbcTemplate.query(
            sql,
            mapOf("publicationId" to publicationId.intValue, "locationTrackId" to locationTrackId.intValue),
        ) { rs, _ ->
            GeometryChangeSummary(
                changedLengthM = rs.getDouble("changed_length_m"),
                maxDistance = rs.getDouble("max_distance"),
                startAddress = TrackMeter(rs.getKmNumber("start_km"), rs.getBigDecimal("start_km_m")),
                endAddress = TrackMeter(rs.getKmNumber("end_km"), rs.getBigDecimal("end_km_m")),
            )
        }
    }

    data class UnprocessedGeometryChange(
        val publicationId: IntId<Publication>,
        val locationTrackId: IntId<LocationTrack>,
        val newAlignmentVersion: RowVersion<LayoutAlignment>,
        val oldAlignmentVersion: RowVersion<LayoutAlignment>?,
        val trackNumberId: IntId<TrackLayoutTrackNumber>,
        val branch: LayoutBranch,
        val publicationTime: Instant,
    )

    fun fetchUnprocessedGeometryChangeRemarks(publicationId: IntId<Publication>): List<UnprocessedGeometryChange> =
        fetchUnprocessedGeometryChangeRemarks(publicationId, null)

    fun fetchUnprocessedGeometryChangeRemarks(maxCount: Int): List<UnprocessedGeometryChange> =
        fetchUnprocessedGeometryChangeRemarks(null, maxCount)

    private fun fetchUnprocessedGeometryChangeRemarks(
        publicationId: IntId<Publication>?,
        maxCount: Int?,
    ): List<UnprocessedGeometryChange> {
        val limitClause = if (maxCount != null) "limit :max_count" else ""
        val sql =
            """
            select
              publication_id,
              publication.design_id,
              location_track_id,
              new_ltv.alignment_id as new_alignment_id,
              new_ltv.alignment_version as new_alignment_version,
              old_ltv.alignment_id as old_alignment_id,
              old_ltv.alignment_version as old_alignment_version,
              new_ltv.track_number_id as track_number_id,
              publication.publication_time
            from publication.location_track plt
              join publication.publication on plt.publication_id = publication.id
              join layout.location_track_version new_ltv
                on plt.location_track_id = new_ltv.id and plt.location_track_version = new_ltv.version
              left join layout.location_track_version old_ltv
                on plt.location_track_id = old_ltv.id and plt.location_track_version = old_ltv.version + 1 and not old_ltv.draft
            where not geometry_change_summary_computed
              and (:publication_id::int is null or publication_id = :publication_id)
            order by publication_id, location_track_id
            $limitClause
        """
                .trimIndent()
        val params = mapOf("publication_id" to publicationId?.intValue, "max_count" to maxCount)
        return jdbcTemplate.query(sql, params) { rs, _ ->
            UnprocessedGeometryChange(
                branch = rs.getLayoutBranch("design_id"),
                publicationId = rs.getIntId("publication_id"),
                locationTrackId = rs.getIntId("location_track_id"),
                newAlignmentVersion = rs.getRowVersion("new_alignment_id", "new_alignment_version"),
                oldAlignmentVersion = rs.getRowVersionOrNull("old_alignment_id", "old_alignment_version"),
                trackNumberId = rs.getIntId("track_number_id"),
                publicationTime = rs.getInstant("publication_time"),
            )
        }
    }

    @Transactional
    fun upsertGeometryChangeSummaries(
        publicationId: IntId<Publication>,
        locationTrackId: IntId<LocationTrack>,
        remarks: List<GeometryChangeSummary>,
    ) {
        val deleteOldsSql =
            """
            delete from publication.location_track_geometry_change_summary
            where publication_id = :publicationId and location_track_id = :locationTrackId
        """
                .trimIndent()
        jdbcTemplate.update(
            deleteOldsSql,
            mapOf("publicationId" to publicationId.intValue, "locationTrackId" to locationTrackId.intValue),
        )

        val insertNewsSql =
            """
            insert into publication.location_track_geometry_change_summary values (
              :publicationId,
              :locationTrackId,
              :remarkOrder,
              :changedLengthM,
              :maxDistance,
              :startKm,
              :endKm,
              :startKmM,
              :endKmM);
        """
                .trimIndent()
        jdbcTemplate.batchUpdate(
            insertNewsSql,
            remarks
                .mapIndexed { index, remark ->
                    mapOf(
                        "publicationId" to publicationId.intValue,
                        "locationTrackId" to locationTrackId.intValue,
                        "remarkOrder" to index,
                        "changedLengthM" to remark.changedLengthM,
                        "maxDistance" to remark.maxDistance,
                        "startKm" to remark.startAddress.kmNumber.toString(),
                        "endKm" to remark.endAddress.kmNumber.toString(),
                        "startKmM" to remark.startAddress.meters,
                        "endKmM" to remark.endAddress.meters,
                    )
                }
                .toTypedArray(),
        )

        val updateOkSql =
            """
            update publication.location_track
            set geometry_change_summary_computed = true
            where publication_id = :publicationId and location_track_id = :locationTrackId
        """
                .trimIndent()
        jdbcTemplate.update(
            updateOkSql,
            mapOf("publicationId" to publicationId.intValue, "locationTrackId" to locationTrackId.intValue),
        )
    }

    fun fetchPublicationKmPostChanges(publicationId: IntId<Publication>): Map<IntId<TrackLayoutKmPost>, KmPostChanges> {
        val sql =
            """
            select 
              km_post.km_post_id,
              km_post.km_post_version,
              km_post_version.km_number,
              old_km_post_version.km_number as old_km_number,
              km_post_version.track_number_id,
              old_km_post_version.track_number_id as old_track_number_id,
              km_post_version.state,
              old_km_post_version.state as old_state,
              postgis.st_x(km_post_version.layout_location) as point_x,
              postgis.st_y(km_post_version.layout_location) as point_y,
              postgis.st_x(old_km_post_version.layout_location) as old_point_x,
              postgis.st_y(old_km_post_version.layout_location) as old_point_y,
              postgis.st_x(km_post_version.gk_location) as gk_point_x,
              postgis.st_y(km_post_version.gk_location) as gk_point_y,
              postgis.st_x(old_km_post_version.gk_location) as old_gk_point_x,
              postgis.st_y(old_km_post_version.gk_location) as old_gk_point_y,
              postgis.st_srid(km_post_version.gk_location) as gk_srid,
              postgis.st_srid(old_km_post_version.gk_location) as old_gk_srid,
              km_post_version.gk_location_confirmed,
              old_km_post_version.gk_location_confirmed as old_gk_location_confirmed,
              km_post_version.gk_location_source,
              old_km_post_version.gk_location_source as old_gk_location_source
            from publication.km_post
              left join layout.km_post_version km_post_version
                      on km_post.km_post_id = km_post_version.id 
                        and km_post.km_post_version = km_post_version.version
                        and km_post.layout_context_id = km_post_version.layout_context_id
              left join layout.km_post_version old_km_post_version
                      on old_km_post_version.id = km_post_version.id 
                        and old_km_post_version.version = km_post_version.version - 1 
                        and old_km_post_version.draft = false
                        and old_km_post_version.design_id is null
            where publication_id = :publication_id
        """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
                val id = rs.getIntId<TrackLayoutKmPost>("km_post_id")
                id to
                    KmPostChanges(
                        id,
                        kmNumber = rs.getChange("km_number", rs::getKmNumberOrNull),
                        trackNumberId = rs.getChange("track_number_id", rs::getIntIdOrNull),
                        state = rs.getChange("state") { rs.getEnumOrNull<LayoutState>(it) },
                        location = rs.getChangePoint("point_x", "point_y"),
                        gkLocation = rs.getChangeGeometryPoint("gk_point_x", "gk_point_y", "gk_srid"),
                        gkSrid = rs.getChange("gk_srid", rs::getSridOrNull),
                        gkLocationConfirmed = rs.getChange("gk_location_confirmed", rs::getBooleanOrNull),
                        gkLocationSource =
                            rs.getChange("gk_location_source") { rs.getEnumOrNull<KmPostGkLocationSource>(it) },
                    )
            }
            .toMap()
            .also { logger.daoAccess(FETCH, KmPostChanges::class, publicationId) }
    }

    fun fetchPublicationReferenceLineChanges(
        publicationId: IntId<Publication>
    ): Map<IntId<ReferenceLine>, ReferenceLineChanges> {
        val sql =
            """
            select
              rlv.id as rl_id,
              rlv.track_number_id as track_number_id,
              old_rlv.track_number_id as old_track_number_id,
              av.length,
              old_av.length as old_length,
              rlv.alignment_id,
              old_rlv.alignment_id as old_alignment_id,
              rlv.alignment_version,
              old_rlv.alignment_version as old_alignment_version,
              postgis.st_x(postgis.st_startpoint(old_sg_first.geometry)) as old_start_x,
              postgis.st_y(postgis.st_startpoint(old_sg_first.geometry)) as old_start_y,
              postgis.st_x(postgis.st_endpoint(old_sg_last.geometry)) as old_end_x,
              postgis.st_y(postgis.st_endpoint(old_sg_last.geometry)) as old_end_y,
              postgis.st_x(postgis.st_startpoint(sg_first.geometry)) as start_x,
              postgis.st_y(postgis.st_startpoint(sg_first.geometry)) as start_y,
              postgis.st_x(postgis.st_endpoint(sg_last.geometry)) as end_x,
              postgis.st_y(postgis.st_endpoint(sg_last.geometry)) as end_y
              from publication.reference_line publication_rl
                left join layout.reference_line_version rlv
                          on publication_rl.reference_line_id = rlv.id
                            and publication_rl.reference_line_version = rlv.version
                            and publication_rl.layout_context_id = rlv.layout_context_id
                left join layout.alignment_version av on rlv.alignment_id = av.id and rlv.alignment_version = av.version
                left join layout.segment_version sv_first
                          on av.id = sv_first.alignment_id
                            and av.version = sv_first.alignment_version
                            and sv_first.segment_index = 0
                left join layout.segment_geometry sg_first on sv_first.geometry_id = sg_first.id
                left join layout.segment_version sv_last
                          on av.id = sv_last.alignment_id
                            and av.version = sv_last.alignment_version
                            and sv_last.segment_index = av.segment_count - 1
                left join layout.segment_geometry sg_last on sv_last.geometry_id = sg_last.id
                left join layout.reference_line_version old_rlv
                          on old_rlv.id = rlv.id
                            and old_rlv.version = rlv.version - 1 
                            and old_rlv.draft = false
                            and old_rlv.design_id is null
                left join layout.alignment_version old_av
                          on old_rlv.alignment_id = old_av.id
                            and old_rlv.alignment_version = old_av.version
                left join layout.segment_version old_sv_first
                          on old_av.id = old_sv_first.alignment_id
                            and old_av.version = old_sv_first.alignment_version
                            and old_sv_first.segment_index = 0
                left join layout.segment_geometry old_sg_first on old_sv_first.geometry_id = old_sg_first.id
                left join layout.segment_version old_sv_last
                          on old_av.id = old_sv_last.alignment_id
                            and old_av.version = old_sv_last.alignment_version
                            and old_sv_last.segment_index = old_av.segment_count - 1
                left join layout.segment_geometry old_sg_last on old_sv_last.geometry_id = old_sg_last.id
            where publication_id = :publication_id
        """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
                val id = rs.getIntId<ReferenceLine>("rl_id")
                id to
                    ReferenceLineChanges(
                        id,
                        trackNumberId = rs.getChange("track_number_id", rs::getIntIdOrNull),
                        length = rs.getChange("length", rs::getDoubleOrNull),
                        startPoint = rs.getChangePoint("start_x", "start_y"),
                        endPoint = rs.getChangePoint("end_x", "end_y"),
                        alignmentVersion = rs.getChangeRowVersion("alignment_id", "alignment_version"),
                    )
            }
            .toMap()
            .also { logger.daoAccess(FETCH, ReferenceLineChanges::class, publicationId) }
    }

    data class PublicationSwitchJoint(
        val jointNumber: JointNumber,
        val locationTrackId: IntId<LocationTrack>,
        val removed: Boolean,
        val point: Point,
        val address: TrackMeter,
        val trackNumberId: IntId<TrackLayoutTrackNumber>,
    )

    fun fetchPublicationSwitchChanges(publicationId: IntId<Publication>): Map<IntId<TrackLayoutSwitch>, SwitchChanges> {
        val sql =
            """
            with joints as (
              select
                switch_id,
                array_agg(location_track_id order by joint_number, location_track_id) as location_track_ids,
                array_agg(joint_number order by joint_number, location_track_id) as joint_numbers,
                array_agg(removed order by joint_number, location_track_id) as removed,
                array_agg(postgis.st_x(point) order by joint_number, location_track_id) as points_x,
                array_agg(postgis.st_y(point) order by joint_number, location_track_id) as points_y,
                array_agg(address order by joint_number, location_track_id) as addresses,
                array_agg(track_number_id order by joint_number, location_track_id) as track_number_ids
                from publication.switch_joint
                  left join publication.publication p on p.id = publication_id
                where publication_id = :publication_id
                group by switch_id
            ),
            location_tracks as (
              select switch_id,
                array_agg(lt.track_number_id order by location_track_id) as lt_track_number_ids,
                array_agg(lt.name order by location_track_id) as lt_names,
                array_agg(lt.id order by location_track_id) as lt_location_track_ids,
                array_agg(lt.design_id order by location_track_id) as lt_design_ids,
                array_agg(
                  greatest(1, lt.version - case when plt.direct_change is true then 1 else 0 end) order by location_track_id
                ) as lt_location_track_old_versions
                from publication.switch_location_tracks
                  left join layout.location_track_version lt
                    on lt.id = location_track_id
                      and lt.version = location_track_version
                      and lt.layout_context_id = location_track_layout_context_id
                  left join lateral
                    (select direct_change
                    from publication.location_track plt
                    where plt.publication_id = :publication_id
                      and plt.location_track_id = switch_location_tracks.location_track_id) plt on (true)
                where publication_id = :publication_id
                group by switch_id
            )
            select
              switch.switch_id,
              switch_version.state_category,
              old_switch_version.state_category as old_state_category,
              switch_version.name,
              old_switch_version.name as old_name,
              switch_version.trap_point,
              old_switch_version.trap_point as old_trap_point,
              switch_owner.name as owner,
              old_switch_owner.name as old_owner,
              switch_structure.type,
              old_switch_structure.type as old_type,
              plan.measurement_method,
              old_plan.measurement_method as old_measurement_method,
              joints.location_track_ids,
              joints.joint_numbers,
              joints.removed,
              joints.points_x,
              joints.points_y,
              joints.addresses,
              joints.track_number_ids,
              switch_structure.presentation_joint_number,
              location_tracks.lt_track_number_ids,
              location_tracks.lt_names,
              location_tracks.lt_location_track_ids,
              location_tracks.lt_design_ids,
              location_tracks.lt_location_track_old_versions
              from publication.switch
                join publication.publication on switch.publication_id = publication.id
                left join layout.switch_version switch_version
                          on switch.switch_id = switch_version.id
                            and switch.switch_version = switch_version.version
                            and switch.layout_context_id = switch_version.layout_context_id
                left join common.switch_structure switch_structure
                          on switch_structure.id = switch_version.switch_structure_id
                left join common.switch_owner
                          on switch_owner.id = switch_version.owner_id
                left join geometry.switch gs
                          on switch_version.geometry_switch_id = gs.id
                left join geometry.plan plan
                          on gs.plan_id = plan.id
                left join layout.switch_version old_switch_version
                          on old_switch_version.id = switch_version.id
                            and old_switch_version.layout_context_id = layout.layout_context_id(publication.design_id, false)
                            and ((old_switch_version.version = switch_version.version - 1 
                              and switch.direct_change = true) 
                              or (old_switch_version.version = switch_version.version
                              and switch.direct_change = false)) 
                            and old_switch_version.draft = false
                left join common.switch_structure old_switch_structure
                          on old_switch_structure.id = old_switch_version.switch_structure_id
                left join common.switch_owner old_switch_owner
                          on old_switch_owner.id = old_switch_version.owner_id
                left join geometry.switch old_gs
                          on old_switch_version.geometry_switch_id = old_gs.id
                left join geometry.plan old_plan
                          on old_gs.plan_id = old_plan.id
                left join joints
                          on joints.switch_id = switch.switch_id
                left join location_tracks
                          on location_tracks.switch_id = switch.switch_id
              where publication_id = :publication_id
        """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
                val presentationJointNumber = rs.getInt("presentation_joint_number").let(::JointNumber)
                val trackNumbers =
                    rs.getListOrNull<Int>("track_number_ids")?.map { IntId<TrackLayoutTrackNumber>(it) } ?: emptyList()
                val locationTracks =
                    rs.getListOrNull<Int>("location_track_ids")?.map { IntId<LocationTrack>(it) } ?: emptyList()
                val jointNumbers = rs.getListOrNull<Int>("joint_numbers") ?: emptyList()
                val removed = rs.getListOrNull<Boolean>("removed") ?: emptyList()
                val points =
                    rs.getListOrNull<Double>("points_x")?.zip(rs.getList<Double>("points_y"))?.map {
                        Point(it.first, it.second)
                    } ?: emptyList()
                val addresses = rs.getListOrNull<String>("addresses")?.map(::TrackMeter) ?: emptyList()
                val joints =
                    jointNumbers.indices
                        .map { index ->
                            PublicationSwitchJoint(
                                jointNumber = JointNumber(jointNumbers[index]),
                                locationTrackId = locationTracks[index],
                                removed = removed[index],
                                point = points[index],
                                address = addresses[index],
                                trackNumberId = trackNumbers[index],
                            )
                        }
                        .filter { joint -> joint.jointNumber == presentationJointNumber }

                val locationTrackNames = rs.getListOrNull<String>("lt_names")?.map(::AlignmentName) ?: emptyList()
                val trackNumberIds =
                    rs.getListOrNull<Int>("lt_track_number_ids")?.map { IntId<TrackLayoutTrackNumber>(it) }
                        ?: emptyList()
                val locationTrackIds =
                    rs.getListOrNull<Int>("lt_location_track_ids")?.let { ids ->
                        rs.getLayoutBranchArrayOrNull("lt_design_ids")?.let { branches ->
                            ids.zip(branches) { id, branch -> LayoutRowId(IntId<LocationTrack>(id), branch.official) }
                        }
                    } ?: emptyList()
                val locationTrackOldVersions = rs.getListOrNull<Int>("lt_location_track_old_versions") ?: emptyList()
                val lts =
                    locationTrackNames.indices.map { index ->
                        SwitchLocationTrack(
                            trackNumberId = trackNumberIds[index],
                            name = locationTrackNames[index],
                            oldVersion = LayoutRowVersion(locationTrackIds[index], locationTrackOldVersions[index]),
                        )
                    }
                val id = rs.getIntId<TrackLayoutSwitch>("switch_id")

                id to
                    SwitchChanges(
                        id,
                        name = rs.getChange("name") { rs.getString(it)?.let(::SwitchName) },
                        state = rs.getChange("state_category") { rs.getEnumOrNull<LayoutStateCategory>(it) },
                        type = rs.getChange("type") { rs.getString(it)?.let(::SwitchType) },
                        trapPoint =
                            rs.getChange("trap_point") {
                                rs.getBooleanOrNull(it).let { value ->
                                    if (value == null) TrapPoint.UNKNOWN else if (value) TrapPoint.YES else TrapPoint.NO
                                }
                            },
                        owner = rs.getChange("owner") { rs.getString(it)?.let(::MetaDataName) },
                        measurementMethod =
                            rs.getChange("measurement_method") { rs.getEnumOrNull<MeasurementMethod>(it) },
                        joints = joints,
                        locationTracks = lts,
                    )
            }
            .toMap()
            .also { logger.daoAccess(FETCH, SwitchChanges::class, publicationId) }
    }

    fun fetchChangeTime(): Instant {
        val sql = "select max(publication_time) as publication_time from publication.publication"
        return jdbcTemplate.query(sql) { rs, _ -> rs.getInstantOrNull("publication_time") }.first()
            ?: Instant.ofEpochSecond(0)
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
                  layout_context_id,
                  track_number_id,
                  start_changed,
                  end_changed,
                  track_number_version,
                  direct_change
                ) 
                select publication.id, track_number.layout_context_id, track_number.id, :start_changed, :end_changed, track_number.version, :direct_change
                from publication.publication
                  inner join layout.track_number_in_layout_context('OFFICIAL', publication.design_id) track_number
                    on track_number.id = :track_number_id
                where publication.id = :publication_id
            """
                .trimMargin(),
            mapOf(true to directChanges, false to indirectChanges)
                .flatMap { (directChange, changes) ->
                    changes.map { change ->
                        mapOf(
                            "publication_id" to publicationId.intValue,
                            "track_number_id" to change.trackNumberId.intValue,
                            "start_changed" to change.isStartChanged,
                            "end_changed" to change.isEndChanged,
                            "direct_change" to directChange,
                        )
                    }
                }
                .toTypedArray(),
        )

        jdbcTemplate.batchUpdate(
            """insert into publication.track_number_km (publication_id, track_number_id, km_number)
               values (:publication_id, :track_number_id, :km_number)
            """
                .trimMargin(),
            (directChanges + indirectChanges)
                .flatMap { tnc ->
                    tnc.changedKmNumbers.map { km ->
                        mapOf(
                            "publication_id" to publicationId.intValue,
                            "track_number_id" to tnc.trackNumberId.intValue,
                            "km_number" to km.toString(),
                        )
                    }
                }
                .toTypedArray(),
        )
    }

    private fun saveKmPostChanges(publicationId: IntId<Publication>, kmPostIds: Collection<IntId<TrackLayoutKmPost>>) {
        jdbcTemplate.batchUpdate(
            """
                insert into publication.km_post (publication_id, layout_context_id, km_post_id, km_post_version)
                select publication.id, km_post.layout_context_id, km_post.id, km_post.version
                from publication.publication
                  inner join layout.km_post_in_layout_context('OFFICIAL', publication.design_id) km_post
                    on km_post.id = :km_post_id
                where publication.id = :publication_id
            """
                .trimMargin(),
            kmPostIds
                .map { id -> mapOf("publication_id" to publicationId.intValue, "km_post_id" to id.intValue) }
                .toTypedArray(),
        )
    }

    private fun saveReferenceLineChanges(
        publicationId: IntId<Publication>,
        referenceLineIds: Collection<IntId<ReferenceLine>>,
    ) {
        jdbcTemplate.batchUpdate(
            """
                insert into publication.reference_line (publication_id, layout_context_id, reference_line_id, reference_line_version)
                select publication.id, reference_line.layout_context_id, reference_line.id, reference_line.version
                from publication.publication
                  inner join layout.reference_line_in_layout_context('OFFICIAL', publication.design_id) reference_line
                    on reference_line.id = :reference_line_id 
                where publication.id = :publication_id
            """
                .trimMargin(),
            referenceLineIds
                .map { id -> mapOf("publication_id" to publicationId.intValue, "reference_line_id" to id.intValue) }
                .toTypedArray(),
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
                  layout_context_id,
                  location_track_id,
                  start_changed,
                  end_changed,
                  location_track_version,
                  direct_change,
                  geometry_change_summary_computed
                )
                select publication.id, location_track.layout_context_id, location_track.id, :start_changed, :end_changed, location_track.version, :direct_change, :geometry_change_summary_computed
                from publication.publication
                  inner join layout.location_track_in_layout_context('OFFICIAL', publication.design_id) location_track 
                    on location_track.id = :location_track_id
                where publication.id = :publication_id
            """
                .trimMargin(),
            mapOf(true to directChanges, false to indirectChanges)
                .flatMap { (directChange, changes) ->
                    changes.map { change ->
                        mapOf(
                            "publication_id" to publicationId.intValue,
                            "location_track_id" to change.locationTrackId.intValue,
                            "start_changed" to change.isStartChanged,
                            "end_changed" to change.isEndChanged,
                            "direct_change" to directChange,
                            "geometry_change_summary_computed" to !directChange,
                        )
                    }
                }
                .toTypedArray(),
        )

        jdbcTemplate.batchUpdate(
            """insert into publication.location_track_km (publication_id, location_track_id, km_number)
               values (:publication_id, :location_track_id, :km_number)
            """
                .trimMargin(),
            (directChanges + indirectChanges)
                .flatMap { ltc ->
                    ltc.changedKmNumbers.map { km ->
                        mapOf(
                            "publication_id" to publicationId.intValue,
                            "location_track_id" to ltc.locationTrackId.intValue,
                            "km_number" to km.toString(),
                        )
                    }
                }
                .toTypedArray(),
        )
    }

    private fun saveSwitchChanges(
        publicationId: IntId<Publication>,
        directChanges: Collection<SwitchChange>,
        indirectChanges: Collection<SwitchChange>,
    ) {
        jdbcTemplate.batchUpdate(
            """
                insert into publication.switch (publication_id, layout_context_id, switch_id, switch_version, direct_change) 
                select publication.id, switch.layout_context_id, switch.id, switch.version, :direct_change
                from publication.publication
                  inner join layout.switch_in_layout_context('OFFICIAL', publication.design_id) switch on switch.id = :switch_id
                where publication.id = :publication_id
            """
                .trimMargin(),
            mapOf(true to directChanges, false to indirectChanges)
                .flatMap { (directChange, changes) ->
                    changes.map { change ->
                        mapOf(
                            "publication_id" to publicationId.intValue,
                            "switch_id" to change.switchId.intValue,
                            "direct_change" to directChange,
                        )
                    }
                }
                .toTypedArray(),
        )

        jdbcTemplate.batchUpdate(
            """
                with publication as (select * from publication.publication where id = :publication_id)
                insert into publication.switch_location_tracks (
                  publication_id,
                  switch_id,
                  location_track_id,
                  location_track_layout_context_id,
                  location_track_version,
                  is_topology_switch
                )
                select distinct :publication_id, :switch_id, location_track.id, location_track.layout_context_id, location_track.version, false
                from publication, layout.location_track_in_layout_context('OFFICIAL', publication.design_id) location_track
                 inner join layout.segment_version on segment_version.alignment_id = location_track.alignment_id
                   and location_track.alignment_version = segment_version.alignment_version
                where segment_version.switch_id = :switch_id 
                union all
                select distinct :publication_id, :switch_id, location_track.id, location_track.layout_context_id, location_track.version, true
                from publication, layout.location_track_in_layout_context('OFFICIAL', publication.design_id) location_track
                where (location_track.topology_start_switch_id = :switch_id or location_track.topology_end_switch_id = :switch_id)
            """
                .trimIndent(),
            (directChanges + indirectChanges)
                .map { s -> mapOf("publication_id" to publicationId.intValue, "switch_id" to s.switchId.intValue) }
                .toTypedArray(),
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
            """
                .trimMargin(),
            (directChanges + indirectChanges)
                .flatMap { s ->
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
                            "track_number_external_id" to cj.trackNumberExternalId,
                        )
                    }
                }
                .toTypedArray(),
        )
    }

    @Cacheable(CACHE_PUBLISHED_LOCATION_TRACKS, sync = true)
    fun fetchPublishedLocationTracks(publicationId: IntId<Publication>): PublishedItemListing<PublishedLocationTrack> {
        val sql =
            """
            select
              ltv.id,
              ltv.design_id,
              ltv.draft,
              ltv.version,
              ltv.name,
              ltv.track_number_id,
              layout.infer_operation_from_location_track_state_transition(ltc.old_state, ltc.state) as operation,
              direct_change,
              changed_km
            from publication.location_track plt
              inner join layout.location_track_version ltv
                on plt.location_track_id = ltv.id and plt.layout_context_id = ltv.layout_context_id and plt.location_track_version = ltv.version
              inner join layout.location_track_change_view ltc
                on ltc.id = plt.location_track_id and ltc.layout_context_id = plt.layout_context_id and ltc.version = plt.location_track_version
              left join lateral(
                select array_remove(array_agg(pltk.km_number), null) as changed_km
                from publication.location_track_km pltk
                where pltk.location_track_id = plt.location_track_id and pltk.publication_id = plt.publication_id
              ) pltk on (true)
            where plt.publication_id = :publication_id
        """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
                rs.getBoolean("direct_change") to
                    PublishedLocationTrack(
                        version = rs.getLayoutRowVersion("id", "design_id", "draft", "version"),
                        name = AlignmentName(rs.getString("name")),
                        trackNumberId = rs.getIntId("track_number_id"),
                        operation = rs.getEnum("operation"),
                        changedKmNumbers = rs.getStringArrayOrNull("changed_km")?.map(::KmNumber)?.toSet() ?: emptySet(),
                    )
            }
            .let { locationTrackRows ->
                logger.daoAccess(FETCH, PublishedLocationTrack::class, locationTrackRows.map { it.second.version })
                partitionDirectIndirectChanges(locationTrackRows)
            }
    }

    fun fetchPublishedReferenceLines(publicationId: IntId<Publication>): List<PublishedReferenceLine> {
        val sql =
            """
            with prev_pub 
              as (select max(publication_time) as prev_publication_time 
              from publication.publication
              where id < :publication_id
                and design_id is not distinct from (select design_id from publication.publication where id = :publication_id)
            )
            select
              rl.id,
              rl.design_id,
              rl.draft,
              rl.version,
              rl.track_number_id,
              layout.infer_operation_from_state_transition(
                  tn_old.state,
                  tn.state
                ) as operation,
              (select coalesce(array_agg(ptnk.km_number), '{}')
               from publication.track_number_km ptnk
               where ptnk.track_number_id = rl.track_number_id and ptnk.publication_id = ptn.publication_id) as changed_km
              from publication.reference_line prl
                inner join layout.reference_line_version rl
                          on rl.id = prl.reference_line_id and rl.layout_context_id = prl.layout_context_id and rl.version = prl.reference_line_version
                left join prev_pub on true
                left join publication.publication p
                          on p.id = prl.publication_id
                left join publication.track_number ptn
                          on ptn.track_number_id = rl.track_number_id and ptn.publication_id = prl.publication_id
                left join layout.track_number_change_view tn
                          on tn.id = ptn.track_number_id and tn.layout_context_id = ptn.layout_context_id and tn.version = ptn.track_number_version
                left join layout.track_number_version tn_old
                          on tn_old.id = ptn.track_number_id
                            and tn_old.layout_context_id = 'main_official'
                            and tn_old.version = tn.version - case when ptn.direct_change then 1 else 0 end
              where prl.publication_id = :publication_id
        """
                .trimIndent()
        return jdbcTemplate
            .query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
                PublishedReferenceLine(
                    version = rs.getLayoutRowVersion("id", "design_id", "draft", "version"),
                    trackNumberId = rs.getIntId("track_number_id"),
                    operation = rs.getEnumOrNull<Operation>("operation") ?: Operation.MODIFY,
                    changedKmNumbers = rs.getStringArray("changed_km").map(::KmNumber).toSet(),
                )
            }
            .also { referenceLines ->
                logger.daoAccess(FETCH, PublishedReferenceLine::class, referenceLines.map { it.version })
            }
    }

    fun fetchPublishedKmPosts(publicationId: IntId<Publication>): List<PublishedKmPost> {
        val sql =
            """
            select
              kmp.id,
              kmp.design_id,
              kmp.draft,
              kmp.version,
              layout.infer_operation_from_state_transition(kpc.old_state, kpc.state) as operation,
              kmp.km_number,
              kmp.track_number_id
            from publication.km_post pkp
              inner join layout.km_post_version kmp 
                on pkp.km_post_id = kmp.id and pkp.layout_context_id = kmp.layout_context_id and pkp.km_post_version = kmp.version
              inner join layout.km_post_change_view kpc
                on kpc.id = pkp.km_post_id and kpc.layout_context_id = pkp.layout_context_id and kpc.version = pkp.km_post_version
            where publication_id = :publication_id
        """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
                PublishedKmPost(
                    version = rs.getLayoutRowVersion("id", "design_id", "draft", "version"),
                    trackNumberId = rs.getIntId("track_number_id"),
                    kmNumber = rs.getKmNumber("km_number"),
                    operation = rs.getEnum("operation"),
                )
            }
            .also { kmPosts -> logger.daoAccess(FETCH, PublishedKmPost::class, kmPosts.map { it.version }) }
    }

    @Transactional(readOnly = true)
    @Cacheable(CACHE_PUBLISHED_SWITCHES, sync = true)
    fun fetchPublishedSwitches(publicationId: IntId<Publication>): PublishedItemListing<PublishedSwitch> {
        val sql =
            """
            select
              sv.id,
              sv.design_id,
              sv.draft,
              sv.version,
              sv.name,
              layout.infer_operation_from_state_category_transition(sc.old_state_category, sc.state_category) as operation,
              direct_change,
              (select coalesce(array_remove(array_agg(distinct lt.track_number_id), null), '{}')
               from layout.location_track_version lt
                 join publication.switch_location_tracks slt
                   on lt.id = slt.location_track_id
                     and lt.version = slt.location_track_version
                     and lt.layout_context_id = slt.location_track_layout_context_id
              where slt.switch_id = ps.switch_id and slt.publication_id = ps.publication_id) as track_number_ids
            from publication.switch ps
              inner join layout.switch_version sv
                on ps.switch_id = sv.id and ps.layout_context_id = sv.layout_context_id and ps.switch_version = sv.version
              inner join layout.switch_change_view sc
                on sc.id = ps.switch_id and sc.layout_context_id = ps.layout_context_id and sc.version = ps.switch_version
            where ps.publication_id = :publication_id
        """
                .trimIndent()

        val publishedSwitchJoints = publishedSwitchJoints(publicationId)

        return jdbcTemplate
            .query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
                rs.getBoolean("direct_change") to
                    PublishedSwitch(
                        version = rs.getLayoutRowVersion("id", "design_id", "draft", "version"),
                        name = SwitchName(rs.getString("name")),
                        trackNumberIds = rs.getIntIdArray<TrackLayoutTrackNumber>("track_number_ids").toSet(),
                        operation = rs.getEnum("operation"),
                        changedJoints =
                            publishedSwitchJoints
                                .filter { it.first == rs.getIntId<TrackLayoutSwitch>("id") }
                                .flatMap { it.second },
                    )
            }
            .let { switchRows ->
                logger.daoAccess(FETCH, PublishedSwitch::class, switchRows.map { it.second.version })
                partitionDirectIndirectChanges(switchRows)
            }
    }

    private fun publishedSwitchJoints(
        publicationId: IntId<Publication>
    ): List<Pair<IntId<TrackLayoutSwitch>, List<SwitchJointChange>>> {
        val sql =
            """
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
        """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
                rs.getIntId<TrackLayoutSwitch>("id") to
                    SwitchJointChange(
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
            .map { (switchId, switchJoints) -> switchId to switchJoints.map { it.second } }
    }

    fun fetchPublishedTrackNumbers(publicationId: IntId<Publication>): PublishedItemListing<PublishedTrackNumber> {
        val sql =
            """
          select
            ptn.track_number_id as id,
            publication.design_id,
            track_number_version as version,
            number,
            layout.infer_operation_from_state_transition(
              old_state,
              state
            ) as operation,
            direct_change,
            (select coalesce(array_remove(array_agg(ptnk.km_number), null), '{}')
             from publication.track_number_km ptnk
             where ptnk.publication_id = ptn.publication_id and ptnk.track_number_id = ptn.track_number_id)
               as changed_km
          from publication.track_number ptn
            inner join layout.track_number_change_view tn
              on tn.id = ptn.track_number_id and tn.layout_context_id = ptn.layout_context_id and tn.version = ptn.track_number_version
            join publication.publication
              on ptn.publication_id = publication.id
          where ptn.publication_id = :publication_id
        """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
                rs.getBoolean("direct_change") to
                    PublishedTrackNumber(
                        version =
                            LayoutRowVersion(
                                rs.getIntId("id"),
                                rs.getLayoutBranch("design_id").official,
                                rs.getInt("version"),
                            ),
                        number = rs.getTrackNumber("number"),
                        operation = rs.getEnum("operation"),
                        changedKmNumbers = rs.getStringArray("changed_km").map(::KmNumber).toSet(),
                    )
            }
            .let { trackNumberRows ->
                logger.daoAccess(FETCH, PublishedTrackNumber::class, trackNumberRows.map { it.second.version })
                partitionDirectIndirectChanges(trackNumberRows)
            }
    }
}

private fun <T> partitionDirectIndirectChanges(rows: List<Pair<Boolean, T>>) =
    rows
        .partition { it.first }
        .let { (directChanges, indirectChanges) ->
            PublishedItemListing(
                directChanges = directChanges.map { it.second },
                indirectChanges = indirectChanges.map { it.second },
            )
        }

private inline fun <reified T> requireUniqueIds(candidates: List<PublicationCandidate<T>>) {
    filterNonUniqueIds(candidates).let { nonUniqueIds ->
        require(nonUniqueIds.isEmpty()) {
            "${T::class.simpleName} publication candidates contained non-unique ids: $nonUniqueIds"
        }
    }
}

private fun <T> filterNonUniqueIds(candidates: List<PublicationCandidate<T>>): Map<IntId<T>, Int> {
    return candidates
        .groupingBy { candidate -> candidate.id }
        .eachCount()
        .filter { candidateAmount -> candidateAmount.value > 1 }
}

private fun publicationCandidateSqlArguments(transition: LayoutContextTransition): Map<String, Any?> =
    mapOf(
        "candidate_design_id" to transition.candidateBranch.designId?.intValue,
        "candidate_state" to transition.candidatePublicationState.name,
        "base_design_id" to transition.baseBranch.designId?.intValue,
    )
