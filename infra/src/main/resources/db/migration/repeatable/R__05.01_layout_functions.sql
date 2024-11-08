-- increment this to force re-execution: 1
create or replace function layout.infer_operation_from_state_transition(old_state layout.state, new_state layout.state)
  returns varchar as
$$
begin
    return (case
      when new_state = 'DELETED' and old_state != 'DELETED' then 'DELETE'
      when new_state != 'DELETED' and old_state = 'DELETED' then 'RESTORE'
      when old_state is null then 'CREATE'
      else 'MODIFY'
    end);
end
$$ language plpgsql called on null input
                    immutable;

create or replace function layout.infer_operation_from_location_track_state_transition(old_state layout.location_track_state, new_state layout.location_track_state)
  returns varchar as
$$
begin
  return (case
            when new_state = 'DELETED' and old_state != 'DELETED' then 'DELETE'
            when new_state != 'DELETED' and old_state = 'DELETED' then 'RESTORE'
            when old_state is null then 'CREATE'
            else 'MODIFY'
          end);
end
$$ language plpgsql called on null input
                    immutable;

create or replace function layout.infer_operation_from_state_category_transition(old_state layout.state_category, new_state layout.state_category)
  returns varchar as
$$
begin
  return (case
            when new_state = 'NOT_EXISTING' and old_state != 'NOT_EXISTING' then 'DELETE'
            when new_state != 'NOT_EXISTING' and old_state = 'NOT_EXISTING' then 'RESTORE'
            when old_state is null then 'CREATE'
            else 'MODIFY'
          end);
end
$$ language plpgsql called on null input
                    immutable;

create or replace function layout.switch_linked_track_numbers(switch_id_in integer, publication_state layout.publication_state, design_id int)
  returns setof integer as
$$
select distinct track_number_id
from layout.location_track_in_layout_context(publication_state, design_id) location_track
where switch_id_in = location_track.topology_start_switch_id
  or switch_id_in = location_track.topology_end_switch_id
  or exists (select *
             from layout.segment_version
             where segment_version.switch_id = switch_id_in
               and segment_version.alignment_id = location_track.alignment_id
               and segment_version.alignment_version = location_track.alignment_version);
$$ language sql stable;
