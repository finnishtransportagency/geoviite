-- increment this to force re-execution: 1
drop function if exists layout.track_number_in_layout_context(layout.publication_state, int);
drop function if exists layout.track_number_in_layout_context(layout.publication_state, int, int);
drop function if exists layout.track_number_is_in_layout_context(layout.publication_state, int, layout.track_number);

create function layout.track_number_is_in_layout_context(publication_state_in layout.publication_state,
                                                         design_id_in int,
                                                         track_number layout.track_number) returns setof empty_type
  language sql
  stable as
$$
select
  where not track_number.cancelled
    and case publication_state_in
          when 'OFFICIAL' then not track_number.draft
          else track_number.draft
            or not exists(select *
                          from layout.track_number overriding_draft
                          where overriding_draft.design_id is not distinct from design_id_in
                            and overriding_draft.draft
                            and not (track_number.design_id is null and overriding_draft.cancelled)
                            and overriding_draft.id = track_number.id)
        end
    and case
          when design_id_in is null then track_number.design_id is null
          else design_id_in = track_number.design_id
            or (track_number.design_id is null
              and not track_number.draft
              and not exists(select *
                               from layout.track_number overriding_design_official
                               where overriding_design_official.design_id = design_id_in
                                 and not overriding_design_official.draft
                                 and not overriding_design_official.cancelled
                                 and overriding_design_official.id = track_number.id
                                 and (publication_state_in = 'OFFICIAL' or not exists (
                                   select *
                                     from layout.track_number design_cancellation
                                     where design_cancellation.design_id = design_id_in
                                       and design_cancellation.draft
                                       and design_cancellation.cancelled
                                       and design_cancellation.id = track_number.id))))
        end
$$;

create function layout.track_number_in_layout_context(publication_state_in layout.publication_state, design_id_in int)
  returns setof layout.track_number
  language sql
  stable
as
$$
select *
from layout.track_number,
  layout.track_number_is_in_layout_context(publication_state_in, design_id_in, track_number)
$$;
