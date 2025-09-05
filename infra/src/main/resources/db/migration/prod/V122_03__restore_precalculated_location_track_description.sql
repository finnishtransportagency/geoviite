alter table layout.location_track
  disable trigger version_update_trigger,
  disable trigger version_row_trigger;

alter table layout.location_track_version
  add column description varchar(256) not null default '';
alter table layout.location_track
  add column description varchar(256) not null default '';

create function v122__parse_switch_name(name varchar)
  returns table
          (
            station      text,
            short_number text
          )
as
$$
select station_and_body[1] as station, coalesce(trimmed_parts, '') as short_number
  from regexp_split_to_array(name, '[\s_]+') as station_and_body,
    lateral regexp_split_to_array(station_and_body[2], '/') body_parts,
    lateral (
             select
               string_agg((
                            select trimmed_name
                              from (
                                select
                                  case
                                    when body_part like 'V%' then substring(body_part from 2)
                                    else body_part
                                  end as number_part
                              ) np,
                                lateral (select length(regexp_substr(number_part, '^[0-9]+')) as number_length) npl,
                                lateral (select
                                           coalesce(substring(number_part for number_length), '') as numeric_number_part,
                                           coalesce(substring(number_part from number_length + 1), '') as number_part_postfix) nnp,
                                lateral (select ltrim(numeric_number_part, '0') as trimmed_numeric_number_part) tnnp,
                                lateral (select greatest(3, length(trimmed_numeric_number_part)) as pad_length) tl,
                                lateral (select
                                           'V' || lpad(trimmed_numeric_number_part, pad_length, '0') ||
                                           number_part_postfix as trimmed_name)
                          ), '/') as trimmed_parts
               from unnest(body_parts) body_part
      )

$$
  language sql immutable;

create function v122__switch_short_number(name varchar) returns text as
$$
select case when short_number = '' then '???' else short_number end
  from v122__parse_switch_name(name) p
$$ language sql;

-- Note: we only handle main-branch here, as there are no designs yet to worry about
update layout.location_track_version lt
set
  description =
    case
      when description_suffix = 'NONE' then
        description_base
      when description_suffix = 'SWITCH_TO_BUFFER' then
        description_base || ' ' ||
        v122__switch_short_number(coalesce(t.start_switch_name, t.end_switch_name)) || ' - Puskin'
      when description_suffix = 'SWITCH_TO_OWNERSHIP_BOUNDARY' then
        description_base || ' ' ||
        v122__switch_short_number(coalesce(t.start_switch_name, t.end_switch_name)) || ' - Omistusraja'
      when description_suffix = 'SWITCH_TO_SWITCH' then
        description_base || ' ' || v122__switch_short_number(t.start_switch_name) || ' - ' ||
        v122__switch_short_number(t.end_switch_name)
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

drop function v122__parse_switch_name(varchar);
drop function v122__switch_short_number(varchar);

update layout.location_track t
set description = v.description
  from layout.location_track_version v
  where v.id = t.id
    and v.layout_context_id = t.layout_context_id
    and v.version = t.version;

alter table layout.location_track
  enable trigger version_update_trigger,
  enable trigger version_row_trigger;
