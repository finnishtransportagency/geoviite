-- Combine reference_line and track_number version histories into a single table

-- ============================================================================
-- PRE-MIGRATION VALIDATION: Verify assumptions about data
-- ============================================================================

-- No design branch versions (algorithm doesn't handle them)
do
$$
  begin
    if exists(
      select 1
        from layout.track_number_version
        where design_id is not null
    ) then
      raise exception 'Migration cannot proceed: track_number_version contains design branch versions.';
    end if;
    if exists(
      select 1
        from layout.reference_line_version
        where design_id is not null
    ) then
      raise exception 'Migration cannot proceed: reference_line_version contains design branch versions.';
    end if;
  end
$$;

-- No zero-length time intervals (interval-calculation logic doesn't handle them)
do
$$
  begin
    if exists(
      select 1
        from layout.track_number_version
        where change_time = expiry_time
    ) then
      raise exception 'Migration cannot proceed: track_number_version contains zero-length time intervals.';
    end if;

    if exists(
      select 1
        from layout.reference_line_version
        where change_time = expiry_time
    ) then
      raise exception 'Migration cannot proceed: reference_line_version contains zero-length time intervals.';
    end if;
  end
$$;

-- No deleted official versions (should never happen)
do
$$
  begin
    if exists(
      select 1
        from layout.track_number_version
        where draft = false and deleted = true
    ) then
      raise exception 'Migration cannot proceed: track_number_version contains deleted official versions.';
    end if;

    if exists(
      select 1
        from layout.reference_line_version
        where draft = false and deleted = true
    ) then
      raise exception 'Migration cannot proceed: reference_line_version contains deleted official versions.';
    end if;
  end
$$;

-- ============================================================================
-- Build the combined version history in temp tables
-- ============================================================================

-- Collect all time boundaries from both tables
drop table if exists all_time_points;
create temporary table all_time_points as
select distinct tn.id as track_number_id, time_point
  from layout.track_number_version tn
    cross join lateral (
      values
        (tn.change_time),
        (tn.expiry_time)
    ) as t(time_point)
  where tn.design_id is null and time_point is not null
union
select distinct rl.track_number_id, time_point
  from layout.reference_line_version rl
    cross join lateral (
      values
        (rl.change_time),
        (rl.expiry_time)
    ) as t(time_point)
  where rl.design_id is null and time_point is not null;

-- At each time-point, there must be exactly 1 track_number_version & reference_line_version per draft state
do
$$
  begin
    if exists(
      select 1
        from all_time_points tp
          inner join layout.track_number_version tnv
                     on tnv.id = tp.track_number_id
                       and tnv.change_time <= tp.time_point
                       and (tnv.expiry_time is null or tnv.expiry_time > tp.time_point)
        where not exists(
          select 1
            from layout.reference_line_version rlv
            where rlv.track_number_id = tp.track_number_id
              and (tnv.draft or not rlv.draft)
              and rlv.change_time <= tp.time_point
              and (rlv.expiry_time is null or rlv.expiry_time > tp.time_point)
        )
    ) then
      raise exception 'Migration cannot proceed: Some track_numbers have no reference_line in all time-points.';
    end if;
  end
$$;

-- Create temporary table to hold the time-intervals of potential combined versions
drop table if exists intervals;
create temporary table intervals as
select distinct
  track_number_id,
  time_point as start_time,
  lead(time_point) over (partition by track_number_id order by time_point) as end_time
  from all_time_points;

-- No zero-length intervals (would indicate duplicate time points)
do
$$
  begin
    if exists(
      select 1 from intervals where start_time is not null and end_time is not null and start_time = end_time
    ) then
      raise exception 'Migration cannot proceed: Interval calculation produced zero-length intervals (start_time = end_time). This indicates duplicate time points in version history.';
    end if;
  end
$$;

-- No overlapping intervals for same track_number
do
$$
  begin
    if exists(
      select i1.track_number_id
        from intervals i1
          join intervals i2 on i1.track_number_id = i2.track_number_id
          and i1.start_time < i2.start_time
          and (i1.end_time is null or i1.end_time > i2.start_time)
        where i1.start_time != i2.start_time or i1.end_time != i2.end_time
    ) then
      raise exception 'Migration cannot proceed: Interval calculation produced overlapping intervals.';
    end if;
  end
$$;

-- Collect the version combinations that should be active on each interval
drop table if exists combined_tn_rl_versions;
create table combined_tn_rl_versions as
with
  -- All interval version combinations (everything that's active in the interval)
  interval_versions as (
    select
      intervals.*,
      coalesce(rlv_o.id, rlv_d.id) as reference_line_id,
      tnv_d.version as tnv_d_version,
      tnv_o.version as tnv_o_version,
      tnv_d.deleted as tnv_d_deleted,
      tnv_d.change_time as tnv_d_change_time,
      rlv_d.version as rlv_d_version,
      rlv_o.version as rlv_o_version,
      rlv_d.deleted as rlv_d_deleted,
      rlv_d.change_time as rlv_d_change_time
      from intervals
        left join layout.track_number_version tnv_d
                  on tnv_d.id = intervals.track_number_id
                    and tnv_d.draft
                    and tnv_d.change_time <= intervals.start_time
                    and (tnv_d.expiry_time is null or tnv_d.expiry_time > intervals.start_time)
        left join layout.track_number_version tnv_o
                  on tnv_o.id = intervals.track_number_id
                    and not tnv_o.draft
                    and tnv_o.change_time <= intervals.start_time
                    and (tnv_o.expiry_time is null or tnv_o.expiry_time > intervals.start_time)
        left join layout.reference_line_version rlv_d
                  on rlv_d.track_number_id = intervals.track_number_id
                    and rlv_d.draft
                    and rlv_d.change_time <= intervals.start_time
                    and (rlv_d.expiry_time is null or rlv_d.expiry_time > intervals.start_time)
        left join layout.reference_line_version rlv_o
                  on rlv_o.track_number_id = intervals.track_number_id
                    and not rlv_o.draft
                    and rlv_o.change_time <= intervals.start_time
                    and (rlv_o.expiry_time is null or rlv_o.expiry_time > intervals.start_time)
      where coalesce(tnv_d.id, tnv_o.id) is not null
        and coalesce(rlv_d.id, rlv_o.id) is not null
  ),
  -- Main official is simply the combination of official rows
  official as (
    select *,
      'main_official' as tn_layout_context_id,
      tnv_o_version as tn_version,
      'main_official' as rl_layout_context_id,
      rlv_o_version as rl_version,
      false as draft,
      false as deleted
      from interval_versions
      where tnv_o_version is not null and rlv_o_version is not null
  ),
  -- Live draft version ignores deleted draft-rows and falls back to official when only one part is drafted
  live_draft as (
    select *,
      (case when tnv_d_deleted = false then 'main_draft' else 'main_official' end) as tn_layout_context_id,
      (case when tnv_d_deleted = false then tnv_d_version else tnv_o_version end) as tn_version,
      (case when rlv_d_deleted = false then 'main_draft' else 'main_official' end) as rl_layout_context_id,
      (case when rlv_d_deleted = false then rlv_d_version else rlv_o_version end) as rl_version,
      true as draft,
      false as deleted
      from interval_versions
    -- At least one part must be in live draft state for the combo-draft to exist
      where (tnv_d_deleted = false or rlv_d_deleted = false)
  ),
  -- Deleted combination rows exist when either both parts are deleted draft or one part's draft is
  -- never even created. Note, that the content should come from the last non-deleted state of the
  -- combination, so if they are deleted at separete times, the older deleted-row was already gone
  -- when the combo was last active, falling back to official for that side → keep that instead.
  deleted_draft as (
    select *,
      (case
         when tnv_d_version is not null and (rlv_d_version is null or tnv_d_change_time >= rlv_d_change_time)
           then 'main_draft'
         else 'main_official'
       end) as tn_layout_context_id,
      (case
         when tnv_d_version is not null and (rlv_d_version is null or tnv_d_change_time >= rlv_d_change_time)
           then tnv_d_version
         else tnv_o_version
       end) as tn_version,
      (case
         when rlv_d_version is not null and (tnv_d_version is null or rlv_d_change_time >= tnv_d_change_time)
           then 'main_draft'
         else 'main_official'
       end) as rl_layout_context_id,
      (case
         when rlv_d_version is not null and (tnv_d_version is null or rlv_d_change_time >= tnv_d_change_time)
           then rlv_d_version
         else rlv_o_version
       end) as rl_version,
      true as draft,
      true as deleted
      from interval_versions v
    -- At least one part must draft & deleted and the other part cannot be undeleted draft (d.deleted is null or true)
      where (tnv_d_deleted = true or rlv_d_deleted = true)
        and (tnv_d_deleted is distinct from false)
        and (rlv_d_deleted is distinct from false)
  ),
  all_intervals as (
    select *
      from live_draft
    union
    select *
      from deleted_draft
    union
    select *
      from official
  ),
  -- Consecutive intervals with identical data can be created by the above and need to be merged
  -- (nothing changed -> no new version). We can just group by the pair versions, as the version
  --  numbers are guaranteed to be increasing (the same combo cannot re-appear later).
  deduped_intervals as (
    select
      track_number_id as tn_id,
      tn_layout_context_id,
      tn_version,
      reference_line_id as rl_id,
      rl_layout_context_id,
      rl_version,
      draft,
      deleted,
      min(start_time) as start_time,
      case
        when count(*) filter (where end_time is null) > 0 then null
        else max(end_time)
      end as end_time
      from all_intervals i
      group by tn_id, tn_layout_context_id, tn_version, rl_id, rl_layout_context_id, rl_version, draft, deleted
  )
select
  tn_id as id,
  case when draft then 'main_draft' else 'main_official' end as layout_context_id,
      row_number() over (partition by tn_id, draft order by start_time, (end_time is null), end_time) as version,
  *
  from deduped_intervals;

-- Every interval must have matched both track_number and reference_line versions
alter table combined_tn_rl_versions
  alter column tn_layout_context_id set not null,
  alter column tn_version set not null,
  alter column rl_layout_context_id set not null,
  alter column rl_version set not null;

-- Validate version continuity in merged versions
do
$$
  begin
    if exists(
      with version_pairs as (
        select
          id,
          draft,
          version,
          lag(version) over (partition by id, draft order by version) as prev_version,
          start_time,
          lag(end_time) over (partition by id, draft order by version) as prev_end_time
          from combined_tn_rl_versions
      )
      select 1
        from version_pairs
        where (prev_version is not null and version != prev_version + 1)
           or (prev_end_time is not null and prev_end_time != start_time)
    ) then
      raise exception 'Migration data error: Combined versions have gaps in version numbers/intervals.';
    end if;
  end
$$;

-- Each id+draft must have an active version (end_time is null)
do
$$
  begin
    if exists(
      with active_check as (
        select distinct id, draft
          from combined_tn_rl_versions
      )
      select ac.id, ac.draft
        from active_check ac
        where not exists(
          select 1
            from combined_tn_rl_versions cv
            where cv.id = ac.id
              and cv.draft = ac.draft
              and cv.end_time is null
        )
    ) then
      raise exception 'Migration data error: Some combined track_numbers have no active version.';
    end if;
  end
$$;

-- Verify coverage: for each source version, the combined rows referencing it must exactly cover its time range.
-- This is the inverse of the generation logic (version → time coverage vs. time → version) and catches
-- dropped versions, wrong boundaries, or versions attributed to the wrong time span.
do
$$
  begin
    if exists(
      with tn_coverage as (
        select
          tnv.id,
          tnv.layout_context_id,
          tnv.version,
          tnv.change_time as expected_from,
          tnv.expiry_time as expected_to,
          min(cv.start_time) as covered_from,
          case
            when count(*) filter (where cv.end_time is null) > 0 then null
            else max(cv.end_time)
          end as covered_to
          from layout.track_number_version tnv
            left join combined_tn_rl_versions cv
                      on cv.id = tnv.id
                        and cv.draft = tnv.draft
                        and cv.tn_layout_context_id = tnv.layout_context_id
                        and cv.tn_version = tnv.version
                        and cv.deleted = false
          where tnv.deleted = false
          group by tnv.id, tnv.layout_context_id, tnv.version, tnv.change_time, tnv.expiry_time
      )
      select 1
        from tn_coverage
        where covered_from is distinct from expected_from
           or covered_to is distinct from expected_to
    ) then
      raise exception 'Merge error: combined versions do not cover original track_number version time ranges faithfully.';
    end if;

    if exists(
      with rl_coverage as (
        select
          rlv.id,
          rlv.track_number_id,
          rlv.layout_context_id,
          rlv.version,
          rlv.change_time as expected_from,
          rlv.expiry_time as expected_to,
          min(cv.start_time) as covered_from,
          case
            when count(*) filter (where cv.end_time is null) > 0 then null
            else max(cv.end_time)
          end as covered_to
          from layout.reference_line_version rlv
            left join combined_tn_rl_versions cv
                      on cv.id = rlv.track_number_id
                        and cv.draft = rlv.draft
                        and cv.rl_id = rlv.id
                        and cv.rl_layout_context_id = rlv.layout_context_id
                        and cv.rl_version = rlv.version
                        and cv.deleted = false
          where rlv.deleted = false
          group by rlv.id, rlv.track_number_id, rlv.layout_context_id, rlv.version, rlv.change_time, rlv.expiry_time
      )
      select *
        from rl_coverage
        where covered_from is distinct from expected_from
           or covered_to is distinct from expected_to
    ) then
      raise exception 'Merge error: combined versions do not cover original reference_line version time ranges faithfully.';
    end if;
  end
$$;

-- Verify deleted flag correctness: a draft combined row should be deleted if and only if
-- neither a live (non-deleted) TN draft nor a live RL draft exists at its start_time.
do
$$
  begin
    if exists(
      select 1
        from combined_tn_rl_versions cv
          left join layout.track_number_version tnv
                    on tnv.id = cv.id
                      and tnv.draft
                      and tnv.deleted = false
                      and tnv.change_time <= cv.start_time
                      and (tnv.expiry_time is null or tnv.expiry_time > cv.start_time)
          left join layout.reference_line_version rlv
                    on rlv.id = cv.rl_id
                      and rlv.draft
                      and rlv.deleted = false
                      and rlv.change_time <= cv.start_time
                      and (rlv.expiry_time is null or rlv.expiry_time > cv.start_time)
        where cv.draft
          and cv.deleted != (tnv.id is null and rlv.id is null)
    ) then
      raise exception 'Merge error: combined draft row deleted flag does not match live draft version existence.';
    end if;
  end
$$;



-- TODO: backup old data (current version tables) into the deprecated -schema

-- TODO: Drop reference constraints from other tables that use these

-- TODO: Disable versioning triggers

-- TODO: Update schema
-- 1. Drop all data from the track_number & version tables
-- 2. Add required columns to the table (can be non-null, as there is no data now)

-- TODO: Migrate data
-- 1. Write versions from the temp table to track_number_version
-- 2. Write the latest version for each id+context_id into track_number

-- TODO: Enable versioning triggers

-- TODO: Update tables that reference this data
-- 1. migrate publication track numbers to reference the correct versions
-- 2. migrate publication reference lines: refer track number instead of reference line (& with the correct versions)
-- 3. other refs that need to be fixed?
-- 4. recreate ref constraints

-- TODO: Migrate alignment model as well
-- 1. Create a new track_number_version_segment table
-- 2. Populate data by joining track_number_version -> alignment -> segment
-- 3. From alignment id+version columns from track_number_version
-- 3. Backup old alignment + segment tables under deprecated schema
-- 4. Drop old alignment & segment tables

-- TODO: Clean this up -- it intentionally breaks the transaction to flyway won't mark this one done yet
do
$$
  begin
    raise exception 'Migration not yet complete';
  end
$$;
