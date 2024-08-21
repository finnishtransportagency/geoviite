drop function if exists layout.switch_in_layout_context(layout.publication_state, int);
drop function if exists layout.switch_in_layout_context(layout.publication_state, int, int);

create function layout.switch_is_in_layout_context(publication_state_in layout.publication_state, design_id_in int,
                                                   switch layout.switch) returns setof empty_type
  language sql
  stable as
$$
select
  where case publication_state_in
          when 'OFFICIAL' then not switch.draft
          else switch.draft
            or case
                 when switch.design_id is null then
                   not exists(select *
                                from layout.switch overriding_draft
                                where overriding_draft.design_id is not distinct from design_id_in
                                  and overriding_draft.draft
                                  and overriding_draft.official_row_id = switch.id)
                 else not exists(select *
                                   from layout.switch overriding_draft
                                   where overriding_draft.design_id is not distinct from design_id_in
                                     and overriding_draft.draft
                                     and overriding_draft.design_row_id = switch.id)
               end
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
                                 and overriding_design_official.official_row_id = switch.id))
        end
$$;

create function layout.switch_in_layout_context(publication_state_in layout.publication_state, design_id_in int,
                                                official_id_in int default null)
  returns table
          (
            row_id              integer,
            official_id         integer,
            design_id           integer,
            draft_id            integer,
            row_version         integer,
            external_id         varchar(50),
            geometry_switch_id  integer,
            name                varchar(50),
            switch_structure_id integer,
            state_category      layout.state_category,
            trap_point          boolean,
            owner_id            integer,
            change_user         varchar(30),
            change_time         timestamptz,
            source              layout.geometry_source,
            draft               boolean
          )
  language sql
  stable
as
$$
select
  row.id as row_id,
  official_id,
  design_id,
  case when row.draft then row.id end as draft_id,
  row.version as row_version,
  row.external_id,
  row.geometry_switch_id,
  row.name,
  row.switch_structure_id,
  row.state_category,
  row.trap_point,
  row.owner_id,
  row.change_user,
  row.change_time,
  row.source,
  row.draft
  from (
    select *
      from layout.switch
      where official_row_id = official_id_in
    union all
    select *
      from layout.switch
      where design_row_id = official_id_in
    union all
    select *
      from layout.switch
      where id = official_id_in
    union all
    select *
      from layout.switch
      where official_id_in is null
  ) row, layout.switch_is_in_layout_context(publication_state_in, design_id_in, row)
$$;
