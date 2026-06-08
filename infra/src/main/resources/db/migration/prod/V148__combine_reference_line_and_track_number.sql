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

-- -- TODO: simplify
-- version_pairs as (
--   select
--     tnv.id as tn_id,
--     tnv.layout_context_id as tn_layout_context_id,
--     tnv.version as tn_version,
--     rlv.id as rl_id,
--     rlv.layout_context_id as rl_layout_context_id,
--     rlv.version as rl_version,
--     i.start_time,
--     i.end_time,
--     false as draft,
--     false as deleted -- official rows are never deleted
--     from intervals i
--       left join layout.track_number_version tnv on tnv.id = i.track_number_id
--       and tnv.design_id is null
--       and tnv.draft = false
--       and tnv.change_time <= i.start_time
--       and (tnv.expiry_time is null or tnv.expiry_time > i.start_time)
--       left join layout.reference_line_version rlv on rlv.track_number_id = i.track_number_id
--       and rlv.design_id is null
--       and rlv.draft = false
--       and rlv.change_time <= i.start_time
--       and (rlv.expiry_time is null or rlv.expiry_time > i.start_time)
--     where tnv.id is not null
--       and rlv.id is not null
-- ),
--
drop table if exists combined_tn_rl_versions;
create table combined_tn_rl_versions as
with official as (
  select
    tnv.id as tn_id,
    tnv.layout_context_id as tn_layout_context_id,
    tnv.version as tn_version,
    rlv.id as rl_id,
    rlv.layout_context_id as rl_layout_context_id,
    rlv.version as rl_version,
    i.start_time,
    i.end_time,
    false as draft,
    false as deleted -- official rows are never deleted
    from intervals i
      left join layout.track_number_version tnv on tnv.id = i.track_number_id
      and tnv.design_id is null
      and tnv.draft = false
      and tnv.change_time <= i.start_time
      and (tnv.expiry_time is null or tnv.expiry_time > i.start_time)
      left join layout.reference_line_version rlv on rlv.track_number_id = i.track_number_id
      and rlv.design_id is null
      and rlv.draft = false
      and rlv.change_time <= i.start_time
      and (rlv.expiry_time is null or rlv.expiry_time > i.start_time)
    where tnv.id is not null
      and rlv.id is not null
),
  live_draft as (
    select
      tnv.id as tn_id,
      tnv.layout_context_id as tn_layout_context_id,
      tnv.version as tn_version,
      rlv.id as rl_id,
      rlv.layout_context_id as rl_layout_context_id,
      rlv.version as rl_version,
      i.start_time,
      i.end_time,
      true as draft,
      false as deleted
      from intervals i
        left join layout.track_number_version tnv on tnv.id = i.track_number_id
        and tnv.design_id is null
        and tnv.deleted = false
        and (tnv.draft = true or not exists(
          select 1
            from layout.track_number_version draft
            where draft.id = i.track_number_id
              and draft.design_id is null
              and draft.draft = true
              and draft.deleted = false
              and draft.change_time <= i.start_time
              and (draft.expiry_time is null or draft.expiry_time > i.start_time)
        ))
        and tnv.change_time <= i.start_time
        and (tnv.expiry_time is null or tnv.expiry_time > i.start_time)
        left join layout.reference_line_version rlv on rlv.track_number_id = i.track_number_id
        and rlv.design_id is null
        and rlv.deleted = false
        and (rlv.draft = true or not exists(
          select 1
            from layout.reference_line_version draft
            where draft.track_number_id = i.track_number_id
              and draft.design_id is null
              and draft.draft = true
              and draft.deleted = false
              and draft.change_time <= i.start_time
              and (draft.expiry_time is null or draft.expiry_time > i.start_time)
        ))
        and rlv.change_time <= i.start_time
        and (rlv.expiry_time is null or rlv.expiry_time > i.start_time)
      where tnv.id is not null
        and rlv.id is not null
        and (rlv.draft or tnv.draft)
  ),
  deleted_draft as (
    select
      tnv.id as tn_id,
      tnv.layout_context_id as tn_layout_context_id,
      tnv.version as tn_version,
      rlv.id as rl_id,
      rlv.layout_context_id as rl_layout_context_id,
      rlv.version as rl_version,
      i.start_time,
      i.end_time,
      true as draft,
      true as deleted
      from intervals i
        left join layout.track_number_version tnv on tnv.id = i.track_number_id
        and tnv.design_id is null
        -- Either: deleted draft OR official (when no live draft exists)
        and ((tnv.draft = true and tnv.deleted = true)
          or (tnv.draft = false and not exists(
            select 1
              from layout.track_number_version draft
              where draft.id = i.track_number_id
                and draft.design_id is null
                and draft.draft = true
                and draft.deleted = false
                and draft.change_time <= i.start_time
                and (draft.expiry_time is null or draft.expiry_time > i.start_time)
          )))
        and tnv.change_time <= i.start_time
        and (tnv.expiry_time is null or tnv.expiry_time > i.start_time)
        left join layout.reference_line_version rlv on rlv.track_number_id = i.track_number_id
        and rlv.design_id is null
        -- Either: deleted draft OR official (when no live draft exists)
        and ((rlv.draft = true and rlv.deleted = true)
          or (rlv.draft = false and not exists(
            select 1
              from layout.reference_line_version draft
              where draft.id = i.track_number_id
                and draft.design_id is null
                and draft.draft = true
                and draft.deleted = false
                and draft.change_time <= i.start_time
                and (draft.expiry_time is null or draft.expiry_time > i.start_time)
          )))
        and rlv.change_time <= i.start_time
        and (rlv.expiry_time is null or rlv.expiry_time > i.start_time)
      where tnv.id is not null
        and rlv.id is not null
        and (rlv.draft or tnv.draft) -- At least one is draft
        and (rlv.deleted or tnv.deleted) -- At least one is deleted
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
      tn_id,
      tn_layout_context_id,
      tn_version,
      rl_id,
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
  from deduped_intervals
;

select * from combined_tn_rl_versions;

-- Now we can query the combined view by joining to the original tables
select
  m.id,
  m.layout_context_id,
  m.version,
  -- Track number fields
  tn.number,
  tn.description,
  tn.state,
  -- Reference line fields
  rl.alignment_id,
  rl.alignment_version,
  rl.start_address,
  -- Version metadata
  m.start_time as change_time,
  m.end_time as expiry_time,
  m.deleted as deleted,
  case
    when rl.change_time >= tn.change_time then rl.change_user
    else tn.change_user
  end as change_user,
  -- For debugging
  m.rl_id as source_reference_line_id,
  m.rl_layout_context_id as source_reference_line_layout_context_id,
  m.rl_version as source_reference_line_version,
  m.tn_layout_context_id as source_track_number_layout_context_id,
  m.tn_version as source_track_number_version
  from combined_tn_rl_versions m
    inner join layout.reference_line_version rl
               on rl.id = m.rl_id and rl.version = m.rl_version and rl.layout_context_id = m.rl_layout_context_id
    inner join layout.track_number_version tn
               on tn.id = m.id and tn.version = m.tn_version and tn.layout_context_id = m.tn_layout_context_id
  order by m.id, m.layout_context_id, m.version;

-- TODO: Next steps:
-- 1. Create the actual combined table structure
-- 2. Insert this data into the new table
-- 3. Update references from other tables using the mapping table
-- 4. Handle the alignment data and segment references
-- 5. Drop or archive the old separate tables
