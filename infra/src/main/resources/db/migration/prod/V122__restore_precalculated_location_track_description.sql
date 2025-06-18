alter table layout.location_track
  disable trigger version_update_trigger;
alter table layout.location_track
  disable trigger version_row_trigger;

alter table layout.location_track_version
  add column description varchar(256) not null default '';
alter table layout.location_track
  add column description varchar(256) not null default '';

select
  t.id,
  t.layout_context_id,
  t.version,
  t.change_time,
  start_sv.name,
  new_start_sv.name,
  end_sv.name,
  new_end_sv.name
  from layout.location_track_version t
    left join layout.location_track_version next
              on t.id = next.id
                and t.layout_context_id = next.layout_context_id
                and t.version = next.version - 1
    left join layout.switch_at(t.change_time) start_sv on t.start_switch_id = start_sv.id and start_sv.layout_context_id = 'main_official'
    left join layout.switch_at(t.change_time) end_sv on t.end_switch_id = end_sv.id and end_sv.layout_context_id = 'main_official'
    left join layout.switch new_start_sv on t.start_switch_id = new_start_sv.id and new_start_sv.layout_context_id = 'main_official'
    left join layout.switch new_end_sv on t.end_switch_id = new_end_sv.id and new_end_sv.layout_context_id = 'main_official'
  where t.layout_context_id = 'main_official'
    and ((start_sv.name is distinct from new_start_sv.name and next.change_time < new_start_sv.change_time) or (end_sv.name is distinct from new_end_sv.name and next.change_time < new_end_sv.change_time))
;

select
  t.id,
  t.layout_context_id,
  t.version,
  t.change_time,
  start_sv.name,
  new_start_sv.name,
  end_sv.name,
  new_end_sv.name
  from layout.location_track_version t
    left join publication.location_track pt
              on t.id = pt.location_track_id
                and t.layout_context_id = pt.layout_context_id
                and t.version = pt.location_track_version
                and pt.direct_change
    left join publication.publication on pt.publication_id = publication.id
--     left join layout.location_track_version next
--               on t.id = next.id
--                 and t.layout_context_id = next.layout_context_id
--                 and t.version = next.version - 1
    left join layout.switch_at(t.change_time) start_sv on t.start_switch_id = start_sv.id and start_sv.layout_context_id = 'main_official'
    left join layout.switch_at(coalesce(publication.publication_time, t.change_time)) new_start_sv on t.start_switch_id = new_start_sv.id and new_start_sv.layout_context_id = 'main_official'
    left join layout.switch_at(t.change_time) end_sv on t.end_switch_id = end_sv.id and end_sv.layout_context_id = 'main_official'
    left join layout.switch_at(coalesce(publication.publication_time, t.change_time)) new_end_sv on t.end_switch_id = new_end_sv.id and new_end_sv.layout_context_id = 'main_official'
--     left join layout.switch new_start_sv on t.start_switch_id = new_start_sv.id and new_start_sv.layout_context_id = 'main_official'
--     left join layout.switch new_end_sv on t.end_switch_id = new_end_sv.id and new_end_sv.layout_context_id = 'main_official'
  where t.layout_context_id = 'main_official'
    and (start_sv.name is distinct from new_start_sv.name or end_sv.name is distinct from new_end_sv.name)
;

with
  version_switch_names as (
    select
      track.id,
      track.layout_context_id,
      track.version,
      track.change_time,
      -- For current draft tracks, we pick the current draft switch names, as those would be sent to ratko after publish. Also, it makes sense to current data for in-progress drafts.
      -- For historical draft track versions, it matters little, but we pick the draft->official switch name from the time of the track version.
      --  * This is "best effort" but may result in the published version using a different name than the draft that was published.
      -- For published tracks, we use the switch names at the time of the track version.
      --  * The version timestamp is the publication timestamp and that moment also determines the data sent to ratko -> corresponds correctly with the version history.
      -- None of this logic properly handles designs (only main-branch switch names), but they should not exist yet in production anyhow.
      track.start_switch_id,
      case
        when track.draft and current_track.id is not null then current_draft_start_sv.name
        when track.draft then coalesce(draft_start_switch.name, official_start_switch.name)
        else official_start_switch.name
      end as start_switch_name,
      track.end_switch_id,
      case
        when track.draft and current_track.id is not null then current_draft_end_sv.name
        when track.draft then coalesce(draft_end_switch.name, official_end_switch.name)
        else official_end_switch.name
      end as end_switch_name
      from layout.location_track_version track
        left join layout.location_track current_track on track.id = current_track.id and track.layout_context_id = current_track.layout_context_id and track.version = current_track.version
        left join lateral (
        select id, name
          from layout.switch_version switch
          where track.draft and switch.draft and switch.id = track.start_switch_id
          order by switch.change_time desc
          limit 1
        ) draft_start_switch on (true)
        left join layout.switch_at(track.change_time) official_start_switch on track.start_switch_id = official_start_switch.id and official_start_switch.layout_context_id = 'main_official'
        left join layout.switch_at(track.change_time) official_end_switch on track.end_switch_id = official_end_switch.id and official_end_switch.layout_context_id = 'main_official'
        left join layout.switch_at(track.change_time) draft_start_switch on track.start_switch_id = draft_start_switch.id and draft_start_switch.layout_context_id = 'main_draft'
        left join layout.switch_at(track.change_time) draft_end_switch on track.end_switch_id = draft_end_switch.id and draft_end_switch.layout_context_id = 'main_draft'
        left join layout.switch_in_layout_context('DRAFT', null) current_draft_start_sv on track.start_switch_id = current_draft_start_sv.id
        left join layout.switch_in_layout_context('DRAFT', null) current_draft_end_sv on track.end_switch_id = current_draft_end_sv.id
  )
select *
  from version_switch_names
  where (start_switch_id is not null and start_switch_name is null) or (end_switch_id is not null and end_switch_name is null)

;


with
  version_switch_names as (
    select
      track.id,
      track.layout_context_id,
      track.version,
      track.change_time,
      -- For current draft tracks, we pick the current draft switch names, as those would be sent to ratko after publish. Also, it makes sense to current data for in-progress drafts.
      -- For historical draft track versions, it matters little, but we pick the draft->official switch name from the time of the track version.
      --  * This is "best effort" but may result in the published version using a different name than the draft that was published.
      -- For published tracks, we use the switch names at the time of the track version.
      --  * The version timestamp is the publication timestamp and that moment also determines the data sent to ratko -> corresponds correctly with the version history.
      -- None of this logic properly handles designs (only main-branch switch names), but they should not exist yet in production anyhow.
      track.start_switch_id,
      case
        when track.draft and current_track.id is not null then current_draft_start_sv.name
        when track.draft then coalesce(draft_start_switch.name, official_start_switch.name)
        else official_start_switch.name
      end as start_switch_name,
      track.end_switch_id,
      case
        when track.draft and current_track.id is not null then current_draft_end_sv.name
        when track.draft then coalesce(draft_end_switch.name, official_end_switch.name)
        else official_end_switch.name
      end as end_switch_name
      from layout.location_track_version track
        left join layout.location_track current_track on track.id = current_track.id and track.layout_context_id = current_track.layout_context_id and track.version = current_track.version
        left join layout.switch_at(track.change_time) official_start_switch on track.start_switch_id = official_start_switch.id and official_start_switch.layout_context_id = 'main_official'
        left join layout.switch_at(track.change_time) official_end_switch on track.end_switch_id = official_end_switch.id and official_end_switch.layout_context_id = 'main_official'
        left join layout.switch_at(track.change_time) draft_start_switch on track.start_switch_id = draft_start_switch.id and draft_start_switch.layout_context_id = 'main_draft'
        left join layout.switch_at(track.change_time) draft_end_switch on track.end_switch_id = draft_end_switch.id and draft_end_switch.layout_context_id = 'main_draft'
        left join layout.switch_in_layout_context('DRAFT', null) current_draft_start_sv on track.start_switch_id = current_draft_start_sv.id
        left join layout.switch_in_layout_context('DRAFT', null) current_draft_end_sv on track.end_switch_id = current_draft_end_sv.id
  )
select *
  from version_switch_names
  where (start_switch_id is not null and start_switch_name is null) or (end_switch_id is not null and end_switch_name is null)


;

  t.id,
  t.layout_context_id,
  t.version,
  start_s.name,
  end_s.name
    from layout.location_track_version t
left join layout.switch_at()
-- TODO: GVT-3080
update layout.location_track_version set description = ...;

update layout.location_track t
set description = v.description
  from layout.location_track_version v
  where v.id = t.id
    and v.layout_context_id = t.layout_context_id
    and v.version = t.version;

alter table layout.location_track
  enable trigger version_update_trigger;
alter table layout.location_track
  enable trigger version_row_trigger;
