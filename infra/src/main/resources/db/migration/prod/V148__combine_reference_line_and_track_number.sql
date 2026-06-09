-- Combine reference_line and track_number version histories into a single table

-- TODO: The algorithm doesn't handle certain corner cases -> before starting, assert that they don't exist:
-- * Designs: no track_number_version or reference_line_version with `design_id is not null`
-- * 0-length time-intervals: no track_number_version or reference_line_version with `change_time = expiry_time`
-- * Deleted official rows: no track_number_version or reference_line_version with `draft = false and deleted = true`

-- Create temporary table to hold the time-intervals of potential combined versions
drop table if exists intervals;
create temporary table intervals as
with
  -- Collect all time boundaries from both tables
  all_time_points as (
    select distinct tn.id as track_number_id, time_point
      from layout.track_number_version tn
        cross join lateral (values
                              (tn.change_time),
                              (tn.expiry_time)) as t(time_point)
      where tn.design_id is null
        and time_point is not null
    union
    select distinct rl.track_number_id, time_point
      from layout.reference_line_version rl
        cross join lateral (values
                              (rl.change_time),
                              (rl.expiry_time)) as t(time_point)
      where rl.design_id is null
        and time_point is not null
  )
select distinct
  track_number_id,
  time_point as start_time,
  lead(time_point) over (partition by track_number_id order by time_point) as end_time
  from all_time_points;

-- TODO: The above should not produce duplicates: assert that
-- select * from tn_intervals where start_time = end_time;

drop table if exists combined_tn_rl_versions;
create table combined_tn_rl_versions as
with
  -- All interval version combinations
  interval_versions as (
    select
      intervals.*,
      coalesce(rlv_o.id, rlv_d.id) as reference_line_id,
      tnv_d.version as tnv_d_version,
      tnv_o.version as tnv_o_version,
      tnv_d.deleted as tnv_d_deleted,
      rlv_d.version as rlv_d_version,
      rlv_o.version as rlv_o_version,
      rlv_d.deleted as rlv_d_deleted
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
  -- TODO: this isn't strictly always correct if, for example:
  -- 1. create drafts of both: officials overridden from both parts
  -- 2. delete both drafts: there is now a deleted draft-version of each, officials in effect
  -- 3. Create draft of only one part: now the official is in effect for the other
  -- 4. Delete the draft part also: now there are deleted versions of both, but the latest one was never paired with
  --    the other deleted part -> the combined deleted shows incorrect info
  -- This could be fixed by picking the deleted row data from the previous non-deleted, rather than the deleted-rows
  --    themselves. Not clear if these cases even exist, and the significance of the exact deleted data is low.
  deleted_draft as (
    select *,
      (case when tnv_d_version is not null then 'main_draft' else 'main_official' end) as tn_layout_context_id,
      coalesce(tnv_d_version, tnv_o_version) as tn_version,
      (case when rlv_d_version is not null then 'main_draft' else 'main_official' end) as rl_layout_context_id,
      coalesce(rlv_d_version, rlv_o_version) as rl_version,
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

select * from combined_tn_rl_versions;

-- TODO: Create the combined version table rows into a temp table from the current tables by joining from the combined versions
-- TODO: Create required mappings from old -> new versions for fixing references

-- TODO: assert data correctness:
-- * At every moment (original time points), active draft & official are in the same state as the original version table
-- * For every id, versions are continuous
-- * For every id, there a version row with no end-time (can be a deleted version)

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
