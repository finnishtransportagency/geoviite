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

create or replace function layout.switch_linked_track_numbers(switch_id_in integer, publication_state text)
  returns setof integer as
$$
select distinct track_number_id
  from (
    (
      select track_number_id
        from layout.segment
          join layout.location_track_publication_view location_track using (alignment_id)
        where switch_id_in = segment.switch_id and publication_state = any(location_track.publication_states)
    )
    union all
    (
      select track_number_id
        from layout.location_track_publication_view location_track
        where (switch_id_in = location_track.topology_start_switch_id
           or switch_id_in = location_track.topology_end_switch_id)
          and publication_state = any(location_track.publication_states)
    )
  ) tns
$$ language sql stable;
