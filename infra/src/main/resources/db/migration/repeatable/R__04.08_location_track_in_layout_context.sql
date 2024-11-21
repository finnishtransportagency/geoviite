-- increment this to force re-execution: 1
drop function if exists layout.location_track_in_layout_context(layout.publication_state, int);
drop function if exists layout.location_track_in_layout_context(layout.publication_state, int, int);
drop function if exists layout.location_track_is_in_layout_context(layout.publication_state, int, layout.location_track);

create function layout.location_track_is_in_layout_context(publication_state_in layout.publication_state,
                                                           design_id_in int,
                                                           location_track layout.location_track) returns setof empty_type
  language sql
  stable as
$$
select
  where not location_track.cancelled
    and case publication_state_in
          when 'OFFICIAL' then not location_track.draft
          else location_track.draft
            or not exists(select *
                          from layout.location_track overriding_draft
                          where overriding_draft.design_id is not distinct from design_id_in
                            and overriding_draft.draft
                            and not (location_track.design_id is null and overriding_draft.cancelled)
                            and overriding_draft.id = location_track.id)
        end
    and case
          when design_id_in is null then location_track.design_id is null
          else design_id_in = location_track.design_id
            or (location_track.design_id is null
              and not location_track.draft
              and not exists(select *
                               from layout.location_track overriding_design_official
                               where overriding_design_official.design_id = design_id_in
                                 and not overriding_design_official.draft
                                 and overriding_design_official.id = location_track.id
                                   and (publication_state_in = 'OFFICIAL' or not exists (
                                     select *
                                       from layout.location_track design_cancellation
                                       where design_cancellation.design_id = design_id_in
                                         and design_cancellation.draft
                                         and design_cancellation.cancelled
                                         and design_cancellation.id = location_track.id))))
        end
$$;

create function layout.location_track_in_layout_context(publication_state_in layout.publication_state, design_id_in int)
  returns setof layout.location_track
  language sql
  stable
as
$$
select *
from layout.location_track,
  layout.location_track_is_in_layout_context(publication_state_in, design_id_in, location_track)
$$;
