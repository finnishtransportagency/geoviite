alter table layout.location_track
  disable trigger version_update_trigger,
  disable trigger version_row_trigger;

alter table layout.location_track_version
  add column description varchar(256) not null default '';
alter table layout.location_track
  add column description varchar(256) not null default '';

-- Note: we only handle main-branch here, as there are no designs yet to worry about
update layout.location_track_version lt
set
  description =
    case
      when description_suffix = 'NONE' then
        description_base
      when description_suffix = 'SWITCH_TO_BUFFER' then
        description_base || ' ' || coalesce(t.start_switch_name, t.end_switch_name, '???') || ' - Puskin'
      when description_suffix = 'SWITCH_TO_OWNERSHIP_BOUNDARY' then
        description_base || ' ' || coalesce(t.start_switch_name, t.end_switch_name, '???') || ' - Omistusraja'
      when description_suffix = 'SWITCH_TO_SWITCH' then
        description_base || ' ' || coalesce(t.start_switch_name, '???') || ' - ' || coalesce(t.end_switch_name, '???')
    end
  from (
    select
      track.id,
      track.layout_context_id,
      track.version,
      track.start_switch_id,
      coalesce(draft_start_sv.name, start_sv.name) as start_switch_name,
      track.end_switch_id,
      coalesce(draft_end_sv.name, end_sv.name) as end_switch_name
      from layout.location_track_version track
        left join layout.switch_at(track.change_time) start_sv
                  on track.start_switch_id = start_sv.id and start_sv.layout_context_id = 'main_official'
        left join layout.switch_at(track.change_time) end_sv
                  on track.end_switch_id = end_sv.id and end_sv.layout_context_id = 'main_official'
        -- The draft names are joined only if the track itself is draft.
        -- Otherwise, these will be null and the coalesce will pick the official names.
        left join layout.switch_at(track.change_time) draft_start_sv
                  on track.draft and track.start_switch_id = draft_start_sv.id and
                     draft_start_sv.layout_context_id = 'main_draft'
        left join layout.switch_at(track.change_time) draft_end_sv
                  on track.draft and track.end_switch_id = draft_end_sv.id and
                     draft_end_sv.layout_context_id = 'main_draft'
  ) as t
  where t.id = lt.id
    and t.layout_context_id = lt.layout_context_id
    and t.version = lt.version;

update layout.location_track t
set description = v.description
  from layout.location_track_version v
  where v.id = t.id
    and v.layout_context_id = t.layout_context_id
    and v.version = t.version;

alter table layout.location_track
  enable trigger version_update_trigger,
  enable trigger version_row_trigger;
