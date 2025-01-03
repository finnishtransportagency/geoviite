-- increment this to force re-execution: 1
drop function if exists layout.switch_in_layout_context(layout.publication_state, int);
drop function if exists layout.switch_in_layout_context(layout.publication_state, int, int);
drop function if exists layout.switch_is_in_layout_context(publication_state_in layout.publication_state, design_id_in integer, switch layout.switch);

create function layout.switch_is_in_layout_context(publication_state_in layout.publication_state, design_id_in int,
                                                   switch layout.switch) returns setof empty_type
  language sql
  stable as
$$
select
  where not switch.cancelled
    and case publication_state_in
          when 'OFFICIAL' then not switch.draft
          else switch.draft
            or not exists(select *
                          from layout.switch overriding_draft
                          where overriding_draft.design_id is not distinct from design_id_in
                            and overriding_draft.draft
                            and not (switch.design_id is null and overriding_draft.cancelled)
                            and overriding_draft.id = switch.id)
        end
    and case
          when design_id_in is null then switch.design_id is null
          else design_id_in = switch.design_id
            or (switch.design_id is null
              and not switch.draft
              and not exists(select *
                               from layout.switch overriding_design_official
                               where overriding_design_official.design_id = design_id_in
                                 and not overriding_design_official.draft
                                 and not overriding_design_official.cancelled
                                 and overriding_design_official.id = switch.id
                                 and (publication_state_in = 'OFFICIAL' or not exists (
                                   select *
                                   from layout.switch design_cancellation
                                     where design_cancellation.design_id = design_id_in
                                       and design_cancellation.draft
                                       and design_cancellation.cancelled
                                       and design_cancellation.id = switch.id))))
        end
$$;

create function layout.switch_in_layout_context(publication_state_in layout.publication_state, design_id_in int)
  returns setof layout.switch
  language sql
  stable
as
$$
select *
from layout.switch,
  layout.switch_is_in_layout_context(publication_state_in, design_id_in, switch)
$$;
