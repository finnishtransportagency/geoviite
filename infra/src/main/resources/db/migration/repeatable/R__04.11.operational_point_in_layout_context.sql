-- increment this to force re-execution: 1
drop function if exists layout.operational_point_in_layout_context(layout.publication_state, int);
drop function if exists layout.operational_point_is_in_layout_context(layout.publication_state, int, layout.operational_point);

create function layout.operational_point_is_in_layout_context(publication_state_in layout.publication_state, design_id_in int,
                                                    operational_point layout.operational_point) returns setof empty_type
  language sql
  stable as
$$
select
  where operational_point.design_asset_state is distinct from 'CANCELLED'
    and case publication_state_in
          when 'OFFICIAL' then not operational_point.draft
          else operational_point.draft
            or not exists(select *
                            from layout.operational_point overriding_draft
                            where overriding_draft.design_id is not distinct from design_id_in
                              and overriding_draft.draft
                              and not (operational_point.design_id is null
                              and overriding_draft.design_asset_state is not distinct from 'CANCELLED')
                              and overriding_draft.id = operational_point.id)
        end
    and case
          when design_id_in is null then operational_point.design_id is null
          else design_id_in = operational_point.design_id
            or (operational_point.design_id is null
              and not operational_point.draft
              and not exists(select *
                               from layout.operational_point overriding_design_official
                               where overriding_design_official.design_id = design_id_in
                                 and not overriding_design_official.draft
                                 and overriding_design_official.design_asset_state is distinct from 'CANCELLED'
                                 and overriding_design_official.id = operational_point.id
                                 and (publication_state_in = 'OFFICIAL' or not exists (
                                 select *
                                   from layout.operational_point design_cancellation
                                   where design_cancellation.design_id = design_id_in
                                     and design_cancellation.draft
                                     and design_cancellation.design_asset_state is not distinct from 'CANCELLED'
                                     and design_cancellation.id = operational_point.id))))
        end
$$;

create function layout.operational_point_in_layout_context(publication_state_in layout.publication_state, design_id_in int)
  returns setof layout.operational_point
  language sql
  stable
as
$$
select *
  from layout.operational_point,
    layout.operational_point_is_in_layout_context(publication_state_in, design_id_in, operational_point)
$$;
