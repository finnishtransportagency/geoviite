package fi.fta.geoviite.infra.publication

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.MeasurementMethod
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.common.assertMainBranch
import fi.fta.geoviite.infra.configuration.staticDataCacheDuration
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.LocationTrackChange
import fi.fta.geoviite.infra.integration.SwitchChange
import fi.fta.geoviite.infra.integration.SwitchJointChange
import fi.fta.geoviite.infra.integration.TrackNumberChange
import fi.fta.geoviite.infra.logging.AccessType.FETCH
import fi.fta.geoviite.infra.logging.AccessType.INSERT
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.switchLibrary.SwitchType
import fi.fta.geoviite.infra.tracklayout.DesignAssetState
import fi.fta.geoviite.infra.tracklayout.KmPostGkLocationSource
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getBboxOrNull
import fi.fta.geoviite.infra.util.getBooleanOrNull
import fi.fta.geoviite.infra.util.getChange
import fi.fta.geoviite.infra.util.getChangeGeometryPoint
import fi.fta.geoviite.infra.util.getChangePoint
import fi.fta.geoviite.infra.util.getChangeRowVersion
import fi.fta.geoviite.infra.util.getDoubleOrNull
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getEnumOrNull
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.getInstantOrNull
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getIntIdArray
import fi.fta.geoviite.infra.util.getIntIdOrNull
import fi.fta.geoviite.infra.util.getJointNumber
import fi.fta.geoviite.infra.util.getJointNumberOrNull
import fi.fta.geoviite.infra.util.getKmNumber
import fi.fta.geoviite.infra.util.getKmNumberOrNull
import fi.fta.geoviite.infra.util.getLayoutBranch
import fi.fta.geoviite.infra.util.getLayoutRowVersion
import fi.fta.geoviite.infra.util.getLayoutRowVersionOrNull
import fi.fta.geoviite.infra.util.getListOrNull
import fi.fta.geoviite.infra.util.getNullableChange
import fi.fta.geoviite.infra.util.getNullableChangePoint
import fi.fta.geoviite.infra.util.getOidOrNull
import fi.fta.geoviite.infra.util.getPoint
import fi.fta.geoviite.infra.util.getPointOrNull
import fi.fta.geoviite.infra.util.getPublicationMessage
import fi.fta.geoviite.infra.util.getPublicationPublishedIn
import fi.fta.geoviite.infra.util.getSridOrNull
import fi.fta.geoviite.infra.util.getStringArray
import fi.fta.geoviite.infra.util.getStringArrayOrNull
import fi.fta.geoviite.infra.util.getTrackMeter
import fi.fta.geoviite.infra.util.getTrackMeterOrNull
import fi.fta.geoviite.infra.util.getTrackNumber
import fi.fta.geoviite.infra.util.getTrackNumberOrNull
import fi.fta.geoviite.infra.util.getUuid
import fi.fta.geoviite.infra.util.queryOptional
import fi.fta.geoviite.infra.util.setUser
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
    val alignmentDao: LayoutAlignmentDao,
) : DaoBase(jdbcTemplateParam) {

    fun fetchTrackNumberPublicationCandidates(
        transition: LayoutContextTransition
    ): List<TrackNumberPublicationCandidate> {
        val sql =
            """
                select id, design_id, draft, version, number, change_time, change_user, design_asset_state,
                  layout.infer_operation_from_state_transition(
                    (select state
                     from layout.track_number_in_layout_context('OFFICIAL', null) tilc
                     where tilc.id = candidate_track_number.id),
                    candidate_track_number.state
                  ) as operation
                from layout.track_number candidate_track_number
                where draft = (:candidate_state = 'DRAFT') and design_id is not distinct from :candidate_design_id
                  and not (design_id is not null and not draft and (design_asset_state = 'CANCELLED' or exists (
                    select * from layout.track_number drafted_cancellation
                    where drafted_cancellation.draft
                      and drafted_cancellation.design_id = candidate_track_number.design_id
                      and drafted_cancellation.id = candidate_track_number.id
                      and drafted_cancellation.design_asset_state = 'CANCELLED')))
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
                    designAssetState = rs.getEnumOrNull<DesignAssetState>("design_asset_state"),
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
                  candidate_reference_line.design_asset_state,
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
                             and (candidate_reference_line.design_asset_state = 'CANCELLED' or exists (
                               select * from layout.reference_line drafted_cancellation
                               where drafted_cancellation.draft
                                 and drafted_cancellation.design_id = candidate_reference_line.design_id
                                 and drafted_cancellation.id = candidate_reference_line.id
                                 and drafted_cancellation.design_asset_state = 'CANCELLED')))
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
                    designAssetState = rs.getEnumOrNull<DesignAssetState>("design_asset_state"),
                    boundingBox = rs.getBboxOrNull("bounding_box"),
                    geometryChanges = null,
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
                  candidate_location_track.design_asset_state,
                  layout.infer_operation_from_location_track_state_transition(
                    (select state
                     from layout.location_track_in_layout_context('OFFICIAL', null) base_lt
                     where base_lt.id = candidate_location_track.id),
                    candidate_location_track.state
                  ) as operation,
                  postgis.st_astext(candidate_location_track.bounding_box) as bounding_box,
                  splits.split_id
                from layout.location_track candidate_location_track
                    left join splits
                        on splits.source_track_id = candidate_location_track.id
                            or candidate_location_track.id = any(splits.target_track_ids)
                            or candidate_location_track.id = any(splits.split_updated_duplicate_ids)
                where candidate_location_track.draft = (:candidate_state = 'DRAFT')
                  and candidate_location_track.design_id is not distinct from :candidate_design_id and
                  not (candidate_location_track.design_id is not null and not candidate_location_track.draft
                       and (design_asset_state = 'CANCELLED' or exists (
                         select * from layout.location_track drafted_cancellation
                         where drafted_cancellation.draft
                           and drafted_cancellation.design_id = candidate_location_track.design_id
                           and drafted_cancellation.id = candidate_location_track.id
                           and drafted_cancellation.design_asset_state = 'CANCELLED')))
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
                    designAssetState = rs.getEnumOrNull<DesignAssetState>("design_asset_state"),
                    publicationGroup = rs.getIntIdOrNull<Split>("split_id")?.let(::PublicationGroup),
                    geometryChanges = null,
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
                  postgis.st_x(joint_version.location) as point_x,
                  postgis.st_y(joint_version.location) as point_y,
                  candidate_switch.design_asset_state,
                  splits.split_id
                from layout.switch candidate_switch
                  left join common.switch_structure on candidate_switch.switch_structure_id = switch_structure.id
                  left join layout.switch_version_joint joint_version
                    on joint_version.switch_id = candidate_switch.id
                      and joint_version.switch_layout_context_id = candidate_switch.layout_context_id
                      and joint_version.switch_version = candidate_switch.version
                      and joint_version.number = switch_structure.presentation_joint_number
                 left join splits on candidate_switch.id = any(splits.split_relinked_switch_ids)
                where candidate_switch.draft = (:candidate_state = 'DRAFT')
                  and candidate_switch.design_id is not distinct from :candidate_design_id
                  and not (candidate_switch.design_id is not null and not candidate_switch.draft
                           and (design_asset_state = 'CANCELLED' or exists (
                             select * from layout.switch drafted_cancellation
                             where drafted_cancellation.draft
                               and drafted_cancellation.design_id = design_id
                               and drafted_cancellation.id = candidate_switch.id
                               and drafted_cancellation.design_asset_state = 'CANCELLED')))
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
                    designAssetState = rs.getEnumOrNull<DesignAssetState>("design_asset_state"),
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
                  candidate_km_post.design_asset_state,
                  postgis.st_x(candidate_km_post.layout_location) as point_x,
                  postgis.st_y(candidate_km_post.layout_location) as point_y
                from layout.km_post candidate_km_post
                where draft = (:candidate_state = 'DRAFT')
                  and design_id is not distinct from :candidate_design_id
                  and not (design_id is not null and not draft and (design_asset_state = 'CANCELLED' or exists (
                    select * from layout.km_post drafted_cancellation
                    where drafted_cancellation.draft
                      and drafted_cancellation.design_id = design_id
                      and drafted_cancellation.id = candidate_km_post.id
                      and drafted_cancellation.design_asset_state = 'CANCELLED')))
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
                    designAssetState = rs.getEnumOrNull<DesignAssetState>("design_asset_state"),
                    location = rs.getPointOrNull("point_x", "point_y"),
                )
            }
        logger.daoAccess(FETCH, KmPostPublicationCandidate::class, candidates.map(KmPostPublicationCandidate::id))
        return candidates
    }

    @Transactional
    fun createPublication(
        layoutBranch: LayoutBranch,
        message: PublicationMessage,
        cause: PublicationCause,
        parentId: IntId<Publication>?,
    ): IntId<Publication> {
        jdbcTemplate.setUser()
        val sql =
            """
                insert into publication.publication(
                  publication_user,
                  publication_time,
                  message,
                  design_id,
                  design_version,
                  cause,
                  parent_publication_id)
                (select
                  current_setting('geoviite.edit_user'),
                  now(),
                  :message,
                  :design_id,
                  (select version from layout.design where id = :design_id),
                  :cause::publication.publication_cause,
                  :parent)
                returning id
            """
                .trimIndent()
        val publicationId: IntId<Publication> =
            jdbcTemplate.queryForObject(
                sql,
                mapOf(
                    "message" to message,
                    "design_id" to layoutBranch.designId?.intValue,
                    "cause" to cause.name,
                    "parent" to parentId?.intValue,
                ),
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
        switchIds: List<IntId<LayoutSwitch>>,
        locationTrackIdsInPublicationUnit: List<IntId<LocationTrack>>? = null,
        includeDeleted: Boolean = false,
    ): Map<IntId<LayoutSwitch>, Set<LayoutRowVersion<LocationTrack>>> {
        if (switchIds.isEmpty()) return mapOf()

        val candidateTrackIncludedCondition =
            if (locationTrackIdsInPublicationUnit == null) "true"
            else if (locationTrackIdsInPublicationUnit.isEmpty()) "false" else "lt.id in (:location_track_ids)"

        // language="sql"
        val sql =
            """
            with
              lt as (
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
              array_agg(distinct lt_s.switch_id) as switch_ids
            from layout.location_track_version_switch_view lt_s
              inner join lt
                   on lt_s.location_track_id = lt.id
                     and lt_s.location_track_layout_context_id = lt.layout_context_id
                     and lt_s.location_track_version = lt.version
            where lt_s.switch_id in (:switch_ids)
              and (:include_deleted or lt.state != 'DELETED')
            group by lt.id, lt.design_id, lt.draft, lt.version
            """
                .trimIndent()

        val params =
            mapOf(
                "switch_ids" to switchIds.map(IntId<LayoutSwitch>::intValue),
                "location_track_ids" to
                    locationTrackIdsInPublicationUnit?.takeIf { it.isNotEmpty() }?.map(IntId<*>::intValue),
                "include_deleted" to includeDeleted,
            ) + target.sqlParameters()
        val result = mutableMapOf<IntId<LayoutSwitch>, Set<LayoutRowVersion<LocationTrack>>>()
        jdbcTemplate
            .query(sql, params) { rs, _ ->
                val trackVersion = rs.getLayoutRowVersion<LocationTrack>("id", "design_id", "draft", "version")
                val switchIdList = rs.getIntIdArray<LayoutSwitch>("switch_ids")
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

    fun getPublication(publicationId: IntId<Publication>) =
        getPublications(setOf(publicationId)).getValue(publicationId)

    fun getPublications(publicationIds: Set<IntId<Publication>>): Map<IntId<Publication>, Publication> {
        val sql =
            """
                select
                  id,
                  publication_uuid,
                  publication_user,
                  publication_time,
                  message,
                  design_id,
                  design_version,
                  cause,
                  parent_publication_id
                from publication.publication
                where publication.id = any(array[:ids]::int[])
            """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("ids" to publicationIds.map { it.intValue })) { rs, _ ->
                val id = rs.getIntId<Publication>("id")
                id to
                    Publication(
                        id = id,
                        uuid = rs.getUuid<Publication>("publication_uuid"),
                        publicationUser = rs.getString("publication_user").let(UserName::of),
                        publicationTime = rs.getInstant("publication_time"),
                        message = rs.getPublicationMessage("message"),
                        layoutBranch =
                            rs.getPublicationPublishedIn("design_id", "design_version", "parent_publication_id"),
                        cause = rs.getEnum("cause"),
                    )
            }
            .associate { it }
            .also { logger.daoAccess(FETCH, Publication::class, publicationIds) }
    }

    @Transactional
    fun insertCalculatedChanges(
        publicationId: IntId<Publication>,
        changes: CalculatedChanges,
        publishedVersions: PublishedVersions,
    ) {
        saveTrackNumberChanges(
            publicationId,
            changes.directChanges.trackNumberChanges,
            changes.indirectChanges.trackNumberChanges,
            publishedVersions.trackNumbers,
        )

        saveKmPostChanges(publicationId, changes.directChanges.kmPostChanges, publishedVersions.kmPosts)

        saveReferenceLineChanges(
            publicationId,
            changes.directChanges.referenceLineChanges,
            publishedVersions.referenceLines,
        )

        saveLocationTrackChanges(
            publicationId,
            changes.directChanges.locationTrackChanges,
            changes.indirectChanges.locationTrackChanges,
            publishedVersions.locationTracks,
        )

        saveSwitchChanges(
            publicationId,
            changes.directChanges.switchChanges,
            changes.indirectChanges.switchChanges,
            publishedVersions.switches,
        )

        logger.daoAccess(INSERT, CalculatedChanges::class, publicationId)
    }

    // Inclusive from/start time, but exclusive to/end time
    fun fetchPublicationsBetween(layoutBranch: LayoutBranch, from: Instant?, to: Instant?): List<Publication> {
        val sql =
            """
                select
                  id,
                  publication_uuid,
                  publication_user,
                  publication_time,
                  message,
                  design_id,
                  design_version,
                  cause,
                  parent_publication_id
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
                    uuid = rs.getUuid("publication_uuid"),
                    publicationUser = rs.getString("publication_user").let(UserName::of),
                    publicationTime = rs.getInstant("publication_time"),
                    message = rs.getPublicationMessage("message"),
                    layoutBranch = rs.getPublicationPublishedIn("design_id", "design_version", "parent_publication_id"),
                    cause = rs.getEnum("cause"),
                )
            }
            .also { publications -> logger.daoAccess(FETCH, Publication::class, publications.map { it.id }) }
    }

    fun list(branchType: LayoutBranchType): List<Publication> {
        val sql =
            """
                select
                  id,
                  publication_uuid,
                  publication_user,
                  publication_time,
                  message,
                  design_id,
                  design_version,
                  cause,
                  parent_publication_id
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
                    uuid = rs.getUuid("publication_uuid"),
                    publicationUser = rs.getString("publication_user").let(UserName::of),
                    publicationTime = rs.getInstant("publication_time"),
                    message = rs.getPublicationMessage("message"),
                    layoutBranch = rs.getPublicationPublishedIn("design_id", "design_version", "parent_publication_id"),
                    cause = rs.getEnum("cause"),
                )
            }
            .also { publications -> logger.daoAccess(FETCH, Publication::class, publications.map { it.id }) }
    }

    fun fetchLatestPublications(branchType: LayoutBranchType, count: Int): List<Publication> {
        val sql =
            """
                select
                  id,
                  publication_uuid,
                  publication_user,
                  publication_time,
                  message,
                  design_id,
                  design_version,
                  cause,
                  parent_publication_id
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
                    uuid = rs.getUuid("publication_uuid"),
                    publicationUser = rs.getString("publication_user").let(UserName::of),
                    publicationTime = rs.getInstant("publication_time"),
                    message = rs.getPublicationMessage("message"),
                    layoutBranch = rs.getPublicationPublishedIn("design_id", "design_version", "parent_publication_id"),
                    cause = rs.getEnum("cause"),
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
    ): Map<IntId<LayoutTrackNumber>, TrackNumberChanges> {
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
                    on tn.id = track_number.id
                      and tn.version = track_number.version
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
                    on old_tn.id = track_number.id
                      and old_tn.version = track_number.base_version
                      and old_tn.layout_context_id = track_number.base_layout_context_id
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
                val id = rs.getIntId<LayoutTrackNumber>("tn_id")
                id to
                    TrackNumberChanges(
                        id,
                        trackNumber = rs.getChange("track_number", rs::getTrackNumberOrNull),
                        description = rs.getChange("description") { rs.getString(it)?.let(::TrackNumberDescription) },
                        state = rs.getChange("state") { rs.getEnumOrNull<LayoutState>(it) },
                        // TODO: these should not be nullable, but current test data contains broken tracknumbers
                        startAddress = rs.getNullableChange("start_address", rs::getTrackMeterOrNull),
                        endPoint = rs.getNullableChangePoint("end_x", "end_y"),
                    )
            }
            .toMap()
            .also { logger.daoAccess(FETCH, TrackNumberChanges::class, publicationId) }
    }

    fun fetchPublicationLocationTrackSwitchLinkChanges(
        publicationId: IntId<Publication>
    ): Map<IntId<LocationTrack>, LocationTrackPublicationSwitchLinkChanges> =
        fetchPublicationLocationTrackSwitchLinkChanges(publicationId, null, null, null, null)[publicationId] ?: mapOf()

    fun fetchPublicationLocationTrackSwitchLinkChanges(
        publicationId: IntId<Publication>?,
        layoutBranch: LayoutBranch?,
        from: Instant?,
        to: Instant?,
        specificObjectId: PublishableObjectIdAndType?,
    ): Map<IntId<Publication>, Map<IntId<LocationTrack>, LocationTrackPublicationSwitchLinkChanges>> {
        require((layoutBranch != null) != (publicationId != null)) {
            """
            |"Must provide exactly one of layoutBranch or publicationId, but provided:
            |layoutBranch=$layoutBranch, publicationId=$publicationId"""
                .trimMargin()
        }
        require(layoutBranch == null || layoutBranch == LayoutBranch.main) { """Only main branch supported""" }

        val sql =
            """
                select
                  change_side,
                  plt.publication_id,
                  plt.id as location_track_id,
                  switch_version.id as switch_id,
                  switch_version.name as switch_name,
                  switch_external_id.external_id as switch_oid
                  from publication.publication
                    join publication.location_track plt on publication.id = plt.publication_id
                    join lateral (
                      select 'new' as change_side, ltv.id, ltv.layout_context_id, ltv.version
                        from layout.location_track_version ltv
                        where plt.id = ltv.id
                          and plt.layout_context_id = ltv.layout_context_id
                          and plt.version = ltv.version
                      union all
                      select 'old' as change_side, ltv.id, ltv.layout_context_id, ltv.version
                        from layout.location_track_version ltv
                        where plt.id = ltv.id
                          and plt.base_layout_context_id = ltv.layout_context_id
                          and plt.base_version = ltv.version
                          and not ltv.draft
                    ) ltv on (true)
                    join lateral (
                      select distinct switch_id from layout.location_track_version_switch_view ltvs
                        where ltvs.location_track_id = ltv.id
                          and ltvs.location_track_layout_context_id = ltv.layout_context_id
                          and ltvs.location_track_version = ltv.version
                    ) switch_ids on (true)
                    join layout.switch_version on switch_ids.switch_id = switch_version.id and not switch_version.draft
                      and switch_version.design_id is null
                    left join layout.switch_external_id
                      on switch_version.id = switch_external_id.id
                        and switch_version.layout_context_id = switch_external_id.layout_context_id
                  where direct_change
                    and not exists(
                      select *
                      from publication.switch psw
                        join publication.publication psw_publication on psw.publication_id = psw_publication.id
                      where psw.id = switch_version.id
                        and psw.layout_context_id = switch_version.layout_context_id
                        and psw_publication.design_id is not distinct from publication.design_id
                        and direct_change
                        and (psw.version = switch_version.version and psw.publication_id > plt.publication_id
                          or psw.version > switch_version.version and psw.publication_id <= plt.publication_id))
                    and case when :publicationId::integer is not null
                          then :publicationId = publication.id
                          else :design_id is not distinct from publication.design_id end
                    and (:from::timestamptz is null or :from <= publication_time)
                    and (:to::timestamptz is null or :to >= publication_time)
                    and (:specific_location_track_id::int is null or :specific_location_track_id = plt.id)
                  order by change_side, switch_id;
            """
                .trimIndent()

        data class ResultRow(
            val changeSide: String,
            val publicationId: IntId<Publication>,
            val locationTrackId: IntId<LocationTrack>,
            val switchId: IntId<LayoutSwitch>,
            val switchName: String,
            val switchOid: Oid<LayoutSwitch>?,
        )

        return jdbcTemplate
            .query(
                sql,
                mapOf(
                    "publicationId" to publicationId?.intValue,
                    "from" to from?.let { Timestamp.from(it) },
                    "to" to to?.let { Timestamp.from(it) },
                    "design_id" to layoutBranch?.designId?.intValue,
                    "specific_location_track_id" to specificObjectId?.locationTrackId()?.intValue,
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
                  location_track.id as location_track_id,
                  location_track.version as location_track_version,
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
                  ltv.length,
                  old_ltv.length as old_length,
                  ltv.track_number_id as track_number_id,
                  old_ltv.track_number_id as old_track_number_id,
                  ltv.owner_id,
                  old_ltv.owner_id as old_owner_id,
                  postgis.st_x(old_ltv_ends.start_point) as old_start_x,
                  postgis.st_y(old_ltv_ends.start_point) as old_start_y,
                  postgis.st_x(old_ltv_ends.end_point) as old_end_x,
                  postgis.st_y(old_ltv_ends.end_point) as old_end_y,
                  postgis.st_x(ltv_ends.start_point) as start_x,
                  postgis.st_y(ltv_ends.start_point) as start_y,
                  postgis.st_x(ltv_ends.end_point) as end_x,
                  postgis.st_y(ltv_ends.end_point) as end_y,
                  geometry_change_summary_computed
                  from publication.location_track
                    join publication.publication on location_track.publication_id = publication.id
                    left join layout.location_track_version ltv
                              on location_track.id = ltv.id
                                and location_track.layout_context_id = ltv.layout_context_id
                                and location_track.version = ltv.version
                    left join layout.location_track_version_ends_view ltv_ends
                              on ltv_ends.id = ltv.id
                                and ltv_ends.layout_context_id = ltv.layout_context_id
                                and ltv_ends.version = ltv.version
                    left join layout.location_track_version old_ltv
                              on old_ltv.id = ltv.id
                                and old_ltv.layout_context_id = location_track.base_layout_context_id
                                and old_ltv.version = location_track.base_version
                    left join layout.location_track_version_ends_view old_ltv_ends
                              on old_ltv.id = old_ltv_ends.id
                                and old_ltv.layout_context_id = old_ltv_ends.layout_context_id
                                and old_ltv.version = old_ltv_ends.version
                  where publication_id = :publication_id
            """
                .trimIndent()

        val summaries = fetchGeometryChangeSummaries(publicationId)
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
                        // TODO: These should not be nullable, but current test data contains broken location tracks
                        endPoint = rs.getNullableChangePoint("end_x", "end_y"),
                        startPoint = rs.getNullableChangePoint("start_x", "start_y"),
                        state = rs.getChange("state") { rs.getEnumOrNull<LocationTrackState>(it) },
                        duplicateOf = rs.getNullableChange("duplicate_of_location_track_id", rs::getIntIdOrNull),
                        type = rs.getChange("type") { rs.getEnumOrNull<LocationTrackType>(it) },
                        length = rs.getChange("length", rs::getDoubleOrNull),
                        geometryChangeSummaries =
                            if (!rs.getBoolean("geometry_change_summary_computed")) null
                            else summaries[id] ?: emptyList(),
                        owner = rs.getChange("owner_id", rs::getIntIdOrNull),
                    )
            }
            .toMap()
            .also { logger.daoAccess(FETCH, LocationTrackChanges::class, publicationId) }
    }

    fun fetchGeometryChangeSummaries(
        publicationId: IntId<Publication>
    ): Map<IntId<LocationTrack>, List<GeometryChangeSummary>> {
        val sql =
            """
                select location_track_id, changed_length_m, max_distance, start_km, end_km, start_km_m, end_km_m
                from publication.location_track_geometry_change_summary
                where publication_id = :publicationId
                order by remark_order
            """
                .trimIndent()
        return jdbcTemplate
            .query(sql, mapOf("publicationId" to publicationId.intValue)) { rs, _ ->
                rs.getIntId<LocationTrack>("location_track_id") to
                    GeometryChangeSummary(
                        changedLengthM = rs.getDouble("changed_length_m"),
                        maxDistance = rs.getDouble("max_distance"),
                        startAddress = TrackMeter(rs.getKmNumber("start_km"), rs.getBigDecimal("start_km_m")),
                        endAddress = TrackMeter(rs.getKmNumber("end_km"), rs.getBigDecimal("end_km_m")),
                    )
            }
            .groupBy({ it.first }, { it.second })
    }

    data class UnprocessedGeometryChange(
        val publicationId: IntId<Publication>,
        val locationTrackId: IntId<LocationTrack>,
        val newTrackVersion: LayoutRowVersion<LocationTrack>,
        val oldTrackVersion: LayoutRowVersion<LocationTrack>?,
        val trackNumberId: IntId<LayoutTrackNumber>,
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
                  plt.publication_id,
                  publication.design_id,
                  plt.id as location_track_id,
                  new_ltv.layout_context_id as new_layout_context_id,
                  new_ltv.version as new_track_version,
                  old_ltv.layout_context_id as old_layout_context_id,
                  old_ltv.version as old_track_version,
                  new_ltv.track_number_id as track_number_id,
                  publication.publication_time
                from publication.location_track plt
                  join publication.publication on plt.publication_id = publication.id
                  join layout.location_track_version new_ltv
                    on plt.id = new_ltv.id and plt.layout_context_id = new_ltv.layout_context_id and plt.version = new_ltv.version
                  left join layout.location_track_version old_ltv
                    on plt.id = old_ltv.id and plt.base_layout_context_id = old_ltv.layout_context_id and plt.base_version = old_ltv.version
                where not geometry_change_summary_computed
                  and (:publication_id::int is null or publication_id = :publication_id)
                order by plt.publication_id, plt.id
                $limitClause
            """
                .trimIndent()
        val params = mapOf("publication_id" to publicationId?.intValue, "max_count" to maxCount)
        return jdbcTemplate.query(sql, params) { rs, _ ->
            UnprocessedGeometryChange(
                branch = rs.getLayoutBranch("design_id"),
                publicationId = rs.getIntId("publication_id"),
                locationTrackId = rs.getIntId("location_track_id"),
                newTrackVersion =
                    rs.getLayoutRowVersion("location_track_id", "new_layout_context_id", "new_track_version"),
                oldTrackVersion =
                    rs.getLayoutRowVersionOrNull("location_track_id", "old_layout_context_id", "old_track_version"),
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
                where publication_id = :publicationId and id = :locationTrackId
            """
                .trimIndent()
        jdbcTemplate.update(
            updateOkSql,
            mapOf("publicationId" to publicationId.intValue, "locationTrackId" to locationTrackId.intValue),
        )
    }

    fun fetchPublicationKmPostChanges(publicationId: IntId<Publication>): Map<IntId<LayoutKmPost>, KmPostChanges> {
        val sql =
            """
                select
                  km_post.id as km_post_id,
                  km_post.version as km_post_version,
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
                          on km_post_version.id = km_post.id
                            and km_post_version.layout_context_id = km_post.layout_context_id
                            and km_post_version.version = km_post.version
                  left join layout.km_post_version old_km_post_version
                          on old_km_post_version.id = km_post.id
                            and old_km_post_version.layout_context_id = km_post.base_layout_context_id
                            and old_km_post_version.version = km_post.base_version
                where publication_id = :publication_id
            """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
                val id = rs.getIntId<LayoutKmPost>("km_post_id")
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
                              on publication_rl.id = rlv.id
                                and publication_rl.layout_context_id = rlv.layout_context_id
                                and publication_rl.version = rlv.version
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
                              on publication_rl.id = old_rlv.id
                                and publication_rl.base_layout_context_id = old_rlv.layout_context_id
                                and publication_rl.base_version = old_rlv.version
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
                        startPoint = rs.getNullableChangePoint("start_x", "start_y"),
                        endPoint = rs.getNullableChangePoint("end_x", "end_y"),
                        alignmentVersion = rs.getChangeRowVersion("alignment_id", "alignment_version"),
                    )
            }
            .toMap()
            .also { logger.daoAccess(FETCH, ReferenceLineChanges::class, publicationId) }
    }

    private enum class ChangeSide {
        OLD,
        NEW,
        NA,
    }

    fun fetchPublicationSwitchChanges(publicationId: IntId<Publication>): Map<IntId<LayoutSwitch>, SwitchChanges> {
        val sql =
            """
              with
                touched_tracks as (
                  select
                    'NEW' as change_side,
                    plt.id as location_track_id,
                    plt.layout_context_id as location_track_layout_context_id,
                    plt.version as location_track_version
                    from publication.location_track plt
                    where plt.publication_id = :publication_id
                  union all
                  select
                    'OLD' as change_side,
                    plt.id as location_track_id,
                    plt.base_layout_context_id as location_track_layout_context_id,
                    plt.base_version as location_track_version
                    from publication.location_track plt
                    where plt.publication_id = :publication_id
                      and plt.base_layout_context_id is not null
                  union all
                  select
                    'NA' as change_side,
                    pslt.location_track_id as location_track_id,
                    pslt.location_track_layout_context_id as location_track_layout_context_id,
                    pslt.location_track_version as location_track_version
                    from publication.switch_location_tracks pslt
                    where pslt.publication_id = :publication_id
                      and not exists (
                        select *
                        from publication.location_track plt
                        where pslt.publication_id = :publication_id
                          and pslt.location_track_id = plt.id
                      )
                ),
                touched_joint_locations as (
                  select
                    joints.switch_id,
                    array_agg(
                      track.change_side
                      order by track.change_side, track.location_track_id, joints.sort
                    ) as track_change_sides,
                    array_agg(
                      track.location_track_id
                      order by track.change_side, track.location_track_id, joints.sort
                    ) as track_ids,
                    array_agg(
                      ltv.track_number_id
                      order by track.change_side, track.location_track_id, joints.sort
                    ) as track_track_number_ids,
                    array_agg(
                      ltv.name
                      order by track.change_side, track.location_track_id, joints.sort
                    ) as track_names,
                    array_agg(
                      joints.joint_number
                      order by track.change_side, track.location_track_id, joints.sort
                    ) as track_joint_numbers,
                    array_agg(
                      postgis.st_x(joints.location)
                      order by track.change_side, track.location_track_id, joints.sort
                    ) as track_joint_locations_x,
                    array_agg(
                      postgis.st_y(joints.location)
                      order by track.change_side, track.location_track_id, joints.sort
                    ) as track_joint_locations_y
                    from touched_tracks track
                      inner join layout.location_track_version ltv
                                 on ltv.id = track.location_track_id
                                   and ltv.layout_context_id = track.location_track_layout_context_id
                                   and ltv.version = track.location_track_version
                      inner join lateral (
                      select
                        start_np.switch_id as switch_id,
                        start_np.switch_joint_number as joint_number,
                        e.start_location as location,
                        0 as sort
                        from layout.location_track_version_edge first_ltv_e
                          inner join layout.edge e on first_ltv_e.edge_id = e.id
                          inner join layout.node_port start_np on start_np.node_id = e.start_node_id and start_np.port = e.start_node_port
                        where first_ltv_e.location_track_id = track.location_track_id
                          and first_ltv_e.location_track_layout_context_id = track.location_track_layout_context_id
                          and first_ltv_e.location_track_version = track.location_track_version
                          and first_ltv_e.edge_index = 0
                      union all
                      select
                        end_np.switch_id as switch_id,
                        end_np.switch_joint_number as joint_number,
                        e.end_location as location,
                        ltv_e.edge_index+1 as sort
                        from layout.location_track_version_edge ltv_e
                          inner join layout.edge e on ltv_e.edge_id = e.id
                          inner join layout.node_port end_np on end_np.node_id = e.end_node_id and end_np.port = e.end_node_port
                        where ltv_e.location_track_id = track.location_track_id
                          and ltv_e.location_track_layout_context_id = track.location_track_layout_context_id
                          and ltv_e.location_track_version = track.location_track_version
                      ) joints on true
                    where joint_number is not null
                    group by joints.switch_id
                )
            select
              switch.id as switch_id,
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
              switch_structure.presentation_joint_number,
              old_switch_structure.presentation_joint_number old_presentation_joint_number,
              joints.track_change_sides,
              joints.track_ids,
              joints.track_track_number_ids,
              joints.track_names,
              joints.track_joint_numbers,
              joints.track_joint_locations_x,
              joints.track_joint_locations_y
              from publication.switch
                join publication.publication on switch.publication_id = publication.id
                left join layout.switch_version switch_version
                          on switch.id = switch_version.id
                            and switch.layout_context_id = switch_version.layout_context_id
                            and switch.version = switch_version.version
                left join common.switch_structure switch_structure
                          on switch_structure.id = switch_version.switch_structure_id
                left join common.switch_owner on switch_owner.id = switch_version.owner_id
                left join geometry.switch gs on switch_version.geometry_switch_id = gs.id
                left join geometry.plan plan on gs.plan_id = plan.id
                left join layout.switch_version old_switch_version
                          on switch.id = old_switch_version.id
                            and switch.base_layout_context_id = old_switch_version.layout_context_id
                            and switch.base_version = old_switch_version.version
                left join common.switch_structure old_switch_structure
                          on old_switch_structure.id = old_switch_version.switch_structure_id
                left join common.switch_owner old_switch_owner on old_switch_owner.id = old_switch_version.owner_id
                left join geometry.switch old_gs on old_switch_version.geometry_switch_id = old_gs.id
                left join geometry.plan old_plan on old_gs.plan_id = old_plan.id
                left join touched_joint_locations joints on joints.switch_id = switch.id
              where publication_id = :publication_id
              """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("publication_id" to publicationId.intValue)) { rs, _ ->
                val presentationJointNumberChange = rs.getChange("presentation_joint_number", rs::getJointNumberOrNull)
                val trackIds = rs.getListOrNull<Int>("track_ids")?.map { IntId<LocationTrack>(it) } ?: emptyList()
                val trackNumberIds =
                    rs.getListOrNull<Int>("track_track_number_ids")?.map { IntId<LayoutTrackNumber>(it) } ?: emptyList()
                val trackChangeSides =
                    rs.getStringArrayOrNull("track_change_sides")?.map { enumValueOf<ChangeSide>(it) } ?: emptyList()
                val trackNames = rs.getStringArrayOrNull("track_names")?.map(::AlignmentName) ?: emptyList()
                val trackJointNumbers = rs.getListOrNull<Int>("track_joint_numbers")?.map(::JointNumber) ?: emptyList()
                val trackJointXs = rs.getListOrNull<Double>("track_joint_locations_x") ?: emptyList()
                val trackJointYs = rs.getListOrNull<Double>("track_joint_locations_y") ?: emptyList()
                val oldTracks = mutableMapOf<IntId<LocationTrack>, SwitchLocationTrack>()
                val newTracks = mutableMapOf<IntId<LocationTrack>, SwitchLocationTrack>()
                val unchangedTracks = mutableMapOf<IntId<LocationTrack>, SwitchLocationTrack>()

                trackIds.forEachIndexed { index, id ->
                    val connectionMap =
                        when (requireNotNull(trackChangeSides[index])) {
                            ChangeSide.OLD -> oldTracks
                            ChangeSide.NEW -> newTracks
                            ChangeSide.NA -> unchangedTracks
                        }
                    val presentationJointNumber =
                        when (requireNotNull(trackChangeSides[index])) {
                            ChangeSide.OLD -> presentationJointNumberChange.old
                            else -> presentationJointNumberChange.new
                        }
                    connectionMap.compute(id) { id, current ->
                        val jointNumber = requireNotNull(trackJointNumbers[index])
                        val joint =
                            PublicationSwitchJoint(
                                jointNumber = jointNumber,
                                location =
                                    Point(requireNotNull(trackJointXs[index]), requireNotNull(trackJointYs[index])),
                                isPresentationJoint = jointNumber == presentationJointNumber,
                            )
                        current?.copy(joints = current.joints + joint)
                            ?: SwitchLocationTrack(
                                id = id,
                                trackNumberId = requireNotNull(trackNumberIds[index]),
                                name = requireNotNull(trackNames[index]),
                                joints = listOf(joint),
                            )
                    }
                }

                val id = rs.getIntId<LayoutSwitch>("switch_id")
                id to
                    SwitchChanges(
                        id,
                        name = rs.getChange("name") { rs.getString(it)?.let(::SwitchName) },
                        state = rs.getChange("state_category") { rs.getEnumOrNull<LayoutStateCategory>(it) },
                        type = rs.getChange("type") { rs.getString(it)?.let(SwitchType::of) },
                        trapPoint =
                            rs.getChange("trap_point") {
                                rs.getBooleanOrNull(it).let { value ->
                                    when (value) {
                                        null -> TrapPoint.UNKNOWN
                                        true -> TrapPoint.YES
                                        false -> TrapPoint.NO
                                    }
                                }
                            },
                        owner = rs.getChange("owner") { rs.getString(it)?.let(::MetaDataName) },
                        measurementMethod =
                            rs.getNullableChange("measurement_method") { rs.getEnumOrNull<MeasurementMethod>(it) },
                        trackConnections =
                            Change(
                                old = (unchangedTracks + oldTracks).values.sortedBy { it.id.intValue },
                                new = (unchangedTracks + newTracks).values.sortedBy { it.id.intValue },
                            ),
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
        publishedVersions: List<Change<LayoutRowVersion<LayoutTrackNumber>>>,
    ) {
        jdbcTemplate.batchUpdate(
            """
                insert into publication.track_number (
                  publication_id,
                  id,
                  layout_context_id,
                  version,
                  base_layout_context_id,
                  base_version,
                  start_changed,
                  end_changed,
                  direct_change
                ) 
                values (
                  :publication_id,
                  :id,
                  :layout_context_id,
                  :version,
                  :base_layout_context_id,
                  :base_version,
                  :start_changed,
                  :end_changed,
                  true
                )
            """
                .trimIndent(),
            directChanges
                .map { change ->
                    val versionChange = requireNotNull(publishedVersions.find { it.new.id == change.trackNumberId })
                    mapOf(
                        "publication_id" to publicationId.intValue,
                        "id" to change.trackNumberId.intValue,
                        "layout_context_id" to versionChange.new.context.toSqlString(),
                        "version" to versionChange.new.version,
                        "base_layout_context_id" to versionChange.old?.context?.toSqlString(),
                        "base_version" to versionChange.old?.version,
                        "start_changed" to change.isStartChanged,
                        "end_changed" to change.isEndChanged,
                    )
                }
                .toTypedArray(),
        )

        jdbcTemplate.batchUpdate(
            """
                insert into publication.track_number (
                  publication_id,
                  id,
                  layout_context_id,
                  version,
                  base_layout_context_id,
                  base_version,
                  start_changed,
                  end_changed,
                  direct_change
                )
                select
                  :publication_id,
                  track_number.id,
                  track_number.layout_context_id,
                  track_number.version,
                  track_number.layout_context_id,
                  track_number.version,
                  :start_changed,
                  :end_changed,
                  false
                from publication.publication,
                  layout.track_number_in_layout_context('OFFICIAL', publication.design_id) track_number
                where publication.id = :publication_id and track_number.id = :track_number_id
            """
                .trimIndent(),
            indirectChanges
                .map { change ->
                    mapOf(
                        "publication_id" to publicationId.intValue,
                        "track_number_id" to change.trackNumberId.intValue,
                        "start_changed" to change.isStartChanged,
                        "end_changed" to change.isEndChanged,
                    )
                }
                .toTypedArray(),
        )

        jdbcTemplate.batchUpdate(
            """
              insert into publication.track_number_km (publication_id, track_number_id, km_number)
              values (:publication_id, :track_number_id, :km_number)
            """
                .trimIndent(),
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

    private fun saveKmPostChanges(
        publicationId: IntId<Publication>,
        kmPostIds: Collection<IntId<LayoutKmPost>>,
        publishedVersions: List<Change<LayoutRowVersion<LayoutKmPost>>>,
    ) {

        jdbcTemplate.batchUpdate(
            """
                insert into publication.km_post (
                  publication_id,
                  id,
                  layout_context_id,
                  version,
                  base_layout_context_id,
                  base_version
                )
                values (
                  :publication_id,
                  :id,
                  :layout_context_id,
                  :version,
                  :base_layout_context_id,
                  :base_version
                )
            """
                .trimIndent(),
            kmPostIds
                .map { id ->
                    val versionChange = requireNotNull(publishedVersions.find { it.new.id == id })
                    mapOf(
                        "publication_id" to publicationId.intValue,
                        "id" to id.intValue,
                        "layout_context_id" to versionChange.new.context.toSqlString(),
                        "version" to versionChange.new.version,
                        "base_layout_context_id" to versionChange.old?.context?.toSqlString(),
                        "base_version" to versionChange.old?.version,
                    )
                }
                .toTypedArray(),
        )
    }

    private fun saveReferenceLineChanges(
        publicationId: IntId<Publication>,
        referenceLineIds: Collection<IntId<ReferenceLine>>,
        publishedVersions: List<Change<LayoutRowVersion<ReferenceLine>>>,
    ) {
        jdbcTemplate.batchUpdate(
            """
                insert into publication.reference_line (
                  publication_id,
                  id,
                  layout_context_id,
                  version,
                  base_layout_context_id,
                  base_version
                )
                values (
                  :publication_id,
                  :id,
                  :layout_context_id,
                  :version,
                  :base_layout_context_id,
                  :base_version
                )
            """
                .trimIndent(),
            referenceLineIds
                .map { id ->
                    val versionChange = requireNotNull(publishedVersions.find { it.new.id == id })
                    mapOf(
                        "publication_id" to publicationId.intValue,
                        "id" to id.intValue,
                        "layout_context_id" to versionChange.new.context.toSqlString(),
                        "version" to versionChange.new.version,
                        "base_layout_context_id" to versionChange.old?.context?.toSqlString(),
                        "base_version" to versionChange.old?.version,
                    )
                }
                .toTypedArray(),
        )
    }

    private fun saveLocationTrackChanges(
        publicationId: IntId<Publication>,
        directChanges: Collection<LocationTrackChange>,
        indirectChanges: Collection<LocationTrackChange>,
        publishedVersions: List<Change<LayoutRowVersion<LocationTrack>>>,
    ) {
        jdbcTemplate.batchUpdate(
            """
                insert into publication.location_track (
                  publication_id,
                  id,
                  layout_context_id,
                  version,
                  base_layout_context_id,
                  base_version,
                  start_changed,
                  end_changed,
                  direct_change,
                  geometry_change_summary_computed
                )
                values (
                  :publication_id,
                  :id,
                  :layout_context_id,
                  :version,
                  :base_layout_context_id,
                  :base_version,
                  :start_changed,
                  :end_changed,
                  true,
                  false
                )
            """
                .trimIndent(),
            directChanges
                .map { change ->
                    val versionChange = requireNotNull(publishedVersions.find { it.new.id == change.locationTrackId })
                    mapOf(
                        "publication_id" to publicationId.intValue,
                        "id" to versionChange.new.id.intValue,
                        "layout_context_id" to versionChange.new.context.toSqlString(),
                        "version" to versionChange.new.version,
                        "base_layout_context_id" to versionChange.old?.context?.toSqlString(),
                        "base_version" to versionChange.old?.version,
                        "start_changed" to change.isStartChanged,
                        "end_changed" to change.isEndChanged,
                    )
                }
                .toTypedArray(),
        )

        jdbcTemplate.batchUpdate(
            """
                insert into publication.location_track (
                  publication_id,
                  id,
                  layout_context_id,
                  version,
                  base_layout_context_id,
                  base_version,
                  start_changed,
                  end_changed,
                  direct_change,
                  geometry_change_summary_computed
                )
                select
                  publication.id,
                  location_track.id,
                  location_track.layout_context_id,
                  location_track.version,
                  location_track.layout_context_id,
                  location_track.version,
                  :start_changed,
                  :end_changed,
                  false,
                  true
                from publication.publication,
                  layout.location_track_in_layout_context('OFFICIAL', publication.design_id) location_track 
                where publication.id = :publication_id
                  and location_track.id = :location_track_id
            """
                .trimIndent(),
            indirectChanges
                .map { change ->
                    mapOf(
                        "publication_id" to publicationId.intValue,
                        "location_track_id" to change.locationTrackId.intValue,
                        "start_changed" to change.isStartChanged,
                        "end_changed" to change.isEndChanged,
                    )
                }
                .toTypedArray(),
        )

        jdbcTemplate.batchUpdate(
            """
              insert into publication.location_track_km (publication_id, location_track_id, km_number)
              values (:publication_id, :location_track_id, :km_number)
            """
                .trimIndent(),
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
        publishedVersions: List<Change<LayoutRowVersion<LayoutSwitch>>>,
    ) {
        jdbcTemplate.batchUpdate(
            """
                insert into publication.switch (
                  publication_id,
                  id,
                  layout_context_id,
                  version,
                  base_layout_context_id,
                  base_version,
                  direct_change
                )
                values (
                  :publication_id,
                  :id,
                  :layout_context_id,
                  :version,
                  :base_layout_context_id,
                  :base_version,
                  true
                )
            """
                .trimIndent(),
            directChanges
                .map { change ->
                    val versionChange = requireNotNull(publishedVersions.find { it.new.id == change.switchId })
                    mapOf(
                        "publication_id" to publicationId.intValue,
                        "id" to change.switchId.intValue,
                        "version" to versionChange.new.version,
                        "layout_context_id" to versionChange.new.context.toSqlString(),
                        "base_version" to versionChange.old?.version,
                        "base_layout_context_id" to versionChange.old?.context?.toSqlString(),
                    )
                }
                .toTypedArray(),
        )

        jdbcTemplate.batchUpdate(
            """
                insert into publication.switch (
                  publication_id,
                  id,
                  layout_context_id,
                  version,
                  base_layout_context_id,
                  base_version,
                  direct_change
                )
                select
                  publication.id,
                  switch.id,
                  switch.layout_context_id,
                  switch.version,
                  switch.layout_context_id,
                  switch.version,
                  false
                from publication.publication, layout.switch_in_layout_context('OFFICIAL', publication.design_id) switch
                where publication.id = :publication_id and switch.id = :switch_id
            """
                .trimIndent(),
            indirectChanges
                .map { change ->
                    mapOf("publication_id" to publicationId.intValue, "switch_id" to change.switchId.intValue)
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
                select distinct :publication_id, :switch_id, location_track.id, location_track.layout_context_id, location_track.version, is_outer_link
                from publication, layout.location_track_in_layout_context('OFFICIAL', publication.design_id) location_track
                 inner join layout.location_track_version_switch_view lt_switch 
                            on lt_switch.location_track_id = location_track.id
                              and lt_switch.location_track_layout_context_id = location_track.layout_context_id
                              and lt_switch.location_track_version = location_track.version
                where lt_switch.switch_id = :switch_id 
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
                .trimIndent(),
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

    private val publishedLocationTracksCache: Cache<IntId<Publication>, PublishedItemListing<PublishedLocationTrack>> =
        Caffeine.newBuilder().maximumSize(500).expireAfterAccess(staticDataCacheDuration).build()

    fun fetchPublishedLocationTracks(
        publicationIds: Set<IntId<Publication>>
    ): Map<IntId<Publication>, PublishedItemListing<PublishedLocationTrack>> =
        publishedLocationTracksCache.getAll(publicationIds, ::fetchPublishedLocationTracksInternal)

    fun fetchPublishedLocationTracksInternal(
        publicationIds: Set<IntId<Publication>>
    ): Map<IntId<Publication>, PublishedItemListing<PublishedLocationTrack>> {
        val sql =
            """
                select
                  plt.publication_id,
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
                  inner join layout.location_track_version ltv using (id, layout_context_id, version)
                  inner join layout.location_track_change_view ltc using (id, layout_context_id, version)
                  left join lateral(
                    select array_remove(array_agg(pltk.km_number), null) as changed_km
                    from publication.location_track_km pltk
                    where pltk.location_track_id = plt.id and pltk.publication_id = plt.publication_id
                  ) pltk on (true)
                where plt.publication_id = any(array[:publication_ids]::int[])
            """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("publication_ids" to publicationIds.map { it.intValue })) { rs, _ ->
                (rs.getIntId<Publication>("publication_id") to rs.getBoolean("direct_change")) to
                    PublishedLocationTrack(
                        version = rs.getLayoutRowVersion("id", "design_id", "draft", "version"),
                        name = AlignmentName(rs.getString("name")),
                        trackNumberId = rs.getIntId("track_number_id"),
                        operation = rs.getEnum("operation"),
                        changedKmNumbers =
                            rs.getStringArrayOrNull("changed_km")?.map(::KmNumber)?.toSet() ?: emptySet(),
                    )
            }
            .let { locationTrackRows ->
                logger.daoAccess(FETCH, PublishedLocationTrack::class, locationTrackRows.map { it.second.version })
                partitionByPublicationIdAndDirectOrIndirect(locationTrackRows)
            }
    }

    fun fetchPublishedReferenceLines(
        publicationIds: Set<IntId<Publication>>
    ): Map<IntId<Publication>, List<PublishedReferenceLine>> {
        val sql =
            """
                select
                  prl.publication_id,
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
                    inner join layout.reference_line_version rl using (id, layout_context_id, version)
                    left join publication.publication p
                              on p.id = prl.publication_id
                    left join publication.track_number ptn
                              on ptn.id = rl.track_number_id and ptn.publication_id = prl.publication_id
                    left join layout.track_number_change_view tn
                              on tn.id = ptn.id and tn.layout_context_id = ptn.layout_context_id and tn.version = ptn.version
                    left join layout.track_number_version tn_old
                              on tn_old.id = ptn.id
                                and tn_old.layout_context_id = ptn.base_layout_context_id
                                and tn_old.version = ptn.base_version
                  where prl.publication_id = any(array[:publication_ids]::int[])
            """
                .trimIndent()
        return jdbcTemplate
            .query(sql, mapOf("publication_ids" to publicationIds.map { it.intValue })) { rs, _ ->
                rs.getIntId<Publication>("publication_id") to
                    PublishedReferenceLine(
                        version = rs.getLayoutRowVersion("id", "design_id", "draft", "version"),
                        trackNumberId = rs.getIntId("track_number_id"),
                        operation = rs.getEnumOrNull<Operation>("operation") ?: Operation.MODIFY,
                        changedKmNumbers = rs.getStringArray("changed_km").map(::KmNumber).toSet(),
                    )
            }
            .also { referenceLines ->
                logger.daoAccess(FETCH, PublishedReferenceLine::class, referenceLines.map { it.second.version })
            }
            .groupBy({ it.first }, { it.second })
    }

    fun fetchPublishedKmPosts(publicationIds: Set<IntId<Publication>>): Map<IntId<Publication>, List<PublishedKmPost>> {
        val sql =
            """
                select
                  pkp.publication_id,
                  kmp.id,
                  kmp.design_id,
                  kmp.draft,
                  kmp.version,
                  layout.infer_operation_from_state_transition(kpc.old_state, kpc.state) as operation,
                  kmp.km_number,
                  kmp.track_number_id
                from publication.km_post pkp
                  inner join layout.km_post_version kmp using (id, layout_context_id, version)
                  inner join layout.km_post_change_view kpc using (id, layout_context_id, version)
                where publication_id = any(array[:publication_ids]::int[])
            """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("publication_ids" to publicationIds.map { it.intValue })) { rs, _ ->
                rs.getIntId<Publication>("publication_id") to
                    PublishedKmPost(
                        version = rs.getLayoutRowVersion("id", "design_id", "draft", "version"),
                        trackNumberId = rs.getIntId("track_number_id"),
                        kmNumber = rs.getKmNumber("km_number"),
                        operation = rs.getEnum("operation"),
                    )
            }
            .also { kmPosts -> logger.daoAccess(FETCH, PublishedKmPost::class, kmPosts.map { it.second.version }) }
            .groupBy({ it.first }, { it.second })
    }

    @Transactional(readOnly = true)
    fun fetchPublishedSwitches(
        publicationIds: Set<IntId<Publication>>
    ): Map<IntId<Publication>, PublishedItemListing<PublishedSwitch>> {
        val sql =
            """
                select
                  ps.publication_id,
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
                  where slt.switch_id = ps.id and slt.publication_id = ps.publication_id) as track_number_ids
                from publication.switch ps
                  inner join layout.switch_version sv using (id, layout_context_id, version)
                  inner join layout.switch_change_view sc using (id, layout_context_id, version)
                where ps.publication_id = any(array[:publication_ids]::int[])
            """
                .trimIndent()

        val publishedSwitchJoints = publishedSwitchJoints(publicationIds)

        return jdbcTemplate
            .query(sql, mapOf("publication_ids" to publicationIds.map { it.intValue })) { rs, _ ->
                val publicationId = rs.getIntId<Publication>("publication_id")
                val switchVersion = rs.getLayoutRowVersion<LayoutSwitch>("id", "design_id", "draft", "version")
                (publicationId to rs.getBoolean("direct_change")) to
                    PublishedSwitch(
                        version = switchVersion,
                        name = SwitchName(rs.getString("name")),
                        trackNumberIds = rs.getIntIdArray<LayoutTrackNumber>("track_number_ids").toSet(),
                        operation = rs.getEnum("operation"),
                        changedJoints =
                            publishedSwitchJoints
                                .getOrDefault(publicationId, mapOf())
                                .getOrDefault(switchVersion.id, listOf()),
                    )
            }
            .let { switchRows ->
                logger.daoAccess(FETCH, PublishedSwitch::class, switchRows.map { it.second.version })
                partitionByPublicationIdAndDirectOrIndirect(switchRows)
            }
    }

    private fun publishedSwitchJoints(
        publicationIds: Set<IntId<Publication>>
    ): Map<IntId<Publication>, Map<IntId<LayoutSwitch>, List<SwitchJointChange>>> {
        val sql =
            """
                select
                  publication_id,
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
                where publication_id = any(array[:publication_ids]::int[])
            """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("publication_ids" to publicationIds.map { it.intValue })) { rs, _ ->
                (rs.getIntId<Publication>("publication_id") to rs.getIntId<LayoutSwitch>("id")) to
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
            .groupBy({ it.first.first }, { it.first.second to it.second })
            .mapValues { (_, idsAndJoints) ->
                idsAndJoints.groupBy({ it.first }).mapValues { (_, js) -> js.map { it.second } }
            }
    }

    fun fetchPublishedTrackNumbers(
        publicationIds: Set<IntId<Publication>>
    ): Map<IntId<Publication>, PublishedItemListing<PublishedTrackNumber>> {
        val sql =
            """
              select
                publication.id as publication_id,
                ptn.id,
                publication.design_id,
                version,
                number,
                layout.infer_operation_from_state_transition(
                  old_state,
                  state
                ) as operation,
                direct_change,
                (select coalesce(array_remove(array_agg(ptnk.km_number), null), '{}')
                 from publication.track_number_km ptnk
                 where ptnk.publication_id = ptn.publication_id and ptnk.track_number_id = ptn.id)
                   as changed_km
              from publication.track_number ptn
                inner join layout.track_number_change_view tn using (id, layout_context_id, version)
                join publication.publication
                  on ptn.publication_id = publication.id
              where ptn.publication_id = any(array[:publication_ids]::int[])
            """
                .trimIndent()

        return jdbcTemplate
            .query(sql, mapOf("publication_ids" to publicationIds.map { it.intValue })) { rs, _ ->
                (rs.getIntId<Publication>("publication_id") to rs.getBoolean("direct_change")) to
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
                partitionByPublicationIdAndDirectOrIndirect(trackNumberRows)
            }
    }

    fun getPreviouslyPublishedDesignVersion(publicationId: IntId<Publication>, designId: IntId<LayoutDesign>): Int? {
        // language="sql"
        val sql =
            """
                select previous_in_design.design_version
                  from publication.publication previous_in_design
                  where design_id = :design_id
                    and publication_time < (
                    select publication_time from publication.publication where id = :publication_id
                  )
                  order by publication_time desc
                  limit 1;
            """
                .trimIndent()
        return jdbcTemplate.queryOptional(
            sql,
            mapOf("publication_id" to publicationId.intValue, "design_id" to designId.intValue),
        ) { rs, _ ->
            rs.getInt("design_version")
        }
    }

    fun fetchPublicationIdByUuid(uuid: Uuid<Publication>): IntId<Publication>? {
        val sql =
            """
                select id from publication.publication 
                where publication_uuid = :publication_uuid::uuid
                limit 1
            """
                .trimIndent()

        return jdbcTemplate.queryOptional(sql, mapOf("publication_uuid" to uuid.toString())) { rs, _ ->
            rs.getIntId("id")
        }
    }

    fun fetchPublicationByUuid(uuid: Uuid<Publication>): Publication? {
        return fetchPublicationIdByUuid(uuid)?.let(::getPublication)
    }

    fun fetchPublishedLocationTracksAfterMoment(
        exclusiveStartMoment: Instant,
        inclusiveEndMoment: Instant,
    ): List<IntId<LocationTrack>> {
        val sql =
            """
            select distinct plt.id as location_track_id
            from publication.location_track plt
              join publication.publication publication on plt.publication_id = publication.id
            where design_id is null 
              and publication.publication_time > :start_time 
              and publication.publication_time <= :end_time;
        """

        val params =
            mapOf(
                "start_time" to Timestamp.from(exclusiveStartMoment),
                "end_time" to Timestamp.from(inclusiveEndMoment),
            )
        return jdbcTemplate.query(sql, params) { rs, _ -> rs.getIntId("location_track_id") }
    }

    fun fetchPublishedTrackNumbersAfterMoment(
        exclusiveStartMoment: Instant,
        inclusiveEndMoment: Instant,
    ): List<IntId<LayoutTrackNumber>> {
        val sql =
            """
            select distinct ptn.id as track_number_id
            from publication.track_number ptn
              join publication.publication publication on ptn.publication_id = publication.id
            where design_id is null 
              and publication.publication_time > :start_time 
              and publication.publication_time <= :end_time;
        """

        val params =
            mapOf(
                "start_time" to Timestamp.from(exclusiveStartMoment),
                "end_time" to Timestamp.from(inclusiveEndMoment),
            )
        return jdbcTemplate.query(sql, params) { rs, _ -> rs.getIntId("track_number_id") }
    }
}

private fun <T> partitionByPublicationIdAndDirectOrIndirect(
    rows: List<Pair<Pair<IntId<Publication>, Boolean>, T>>
): Map<IntId<Publication>, PublishedItemListing<T>> =
    rows.groupBy({ it.first.first }, { it.first.second to it.second }).mapValues { (_, publicationRows) ->
        partitionDirectIndirectChanges(publicationRows)
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

private inline fun <reified T : LayoutAsset<T>> requireUniqueIds(candidates: List<PublicationCandidate<T>>) {
    filterNonUniqueIds(candidates).let { nonUniqueIds ->
        require(nonUniqueIds.isEmpty()) {
            "${T::class.simpleName} publication candidates contained non-unique ids: $nonUniqueIds"
        }
    }
}

private fun <T : LayoutAsset<T>> filterNonUniqueIds(candidates: List<PublicationCandidate<T>>): Map<IntId<T>, Int> {
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
