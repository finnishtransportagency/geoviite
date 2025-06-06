create or replace function layout.switch_linked_track_numbers(switch_id_in integer,
                                                              publication_state layout.publication_state, design_id int)
  returns setof integer as
$$
select distinct track_number_id
  from layout.location_track_in_layout_context(publication_state, design_id) lt
  where exists (
    select *
      from layout.location_track_version_switch_view lt_s
      where lt_s.location_track_id = lt.id
        and lt_s.location_track_layout_context_id = lt.layout_context_id
        and lt_s.location_track_version = lt.version
        and lt_s.switch_id = switch_id_in
  );
$$ language sql stable;
