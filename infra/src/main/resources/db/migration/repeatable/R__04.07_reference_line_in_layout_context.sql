drop function if exists layout.reference_line_in_layout_context(layout.publication_state, int);
drop function if exists layout.reference_line_in_layout_context(layout.publication_state, int, int);

drop function if exists layout.reference_line_is_in_layout_context(layout.publication_state, int, layout.reference_line);

create function layout.reference_line_is_in_layout_context(publication_state_in layout.publication_state,
                                                           design_id_in int,
                                                           reference_line layout.reference_line) returns setof empty_type
  language sql
  stable as
$$
select
  where reference_line.design_asset_state is distinct from 'CANCELLED'
    and case publication_state_in
          when 'OFFICIAL' then not reference_line.draft
          else reference_line.draft
            or not exists(select *
                          from layout.reference_line overriding_draft
                          where overriding_draft.design_id is not distinct from design_id_in
                            and overriding_draft.draft
                            and not (reference_line.design_id is null
                                      and overriding_draft.design_asset_state is not distinct from 'CANCELLED')
                            and overriding_draft.id = reference_line.id)
        end
    and case
          when design_id_in is null then reference_line.design_id is null
          else design_id_in = reference_line.design_id
            or (reference_line.design_id is null
              and not reference_line.draft
              and not exists(select *
                               from layout.reference_line overriding_design_official
                               where overriding_design_official.design_id = design_id_in
                                 and not overriding_design_official.draft
                                 and overriding_design_official.design_asset_state is distinct from 'CANCELLED'
                                 and overriding_design_official.id = reference_line.id
                                   and (publication_state_in = 'OFFICIAL' or not exists (
                                     select *
                                       from layout.reference_line design_cancellation
                                       where design_cancellation.design_id = design_id_in
                                         and design_cancellation.draft
                                         and design_cancellation.design_asset_state is not distinct from 'CANCELLED'
                                         and design_cancellation.id = reference_line.id))))
        end
$$;

create function layout.reference_line_in_layout_context(publication_state_in layout.publication_state, design_id_in int)
  returns setof layout.reference_line
  language sql
  stable
as
$$
select *
from layout.reference_line,
     layout.reference_line_is_in_layout_context(publication_state_in, design_id_in, reference_line)
$$
