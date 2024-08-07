drop function if exists layout.km_post_in_layout_context(layout.publication_state, int);
drop function if exists layout.km_post_in_layout_context(layout.publication_state, int, int);

create function layout.km_post_in_layout_context(publication_state_in layout.publication_state, design_id_in int,
                                                 official_id_in int default null)
  returns table
          (
            row_id              integer,
            official_id         integer,
            design_id           integer,
            draft_id            integer,
            row_version         integer,
            track_number_id     integer,
            geometry_km_post_id integer,
            km_number           varchar(6),
            layout_location     postgis.geometry(Point, 3067),
            gk_location         postgis.geometry(Point),
            state               layout.state,
            change_user         varchar(30),
            change_time         timestamptz,
            draft               boolean
          )
  language sql
  stable
as
$$
select
  row.id as row_id,
  coalesce(official_row_id, design_row_id, row.id) as official_id,
  design_id,
  case when row.draft then row.id end as draft_id,
  row.version as row_version,
  row.track_number_id,
  row.geometry_km_post_id,
  row.km_number,
  row.layout_location,
  row.gk_location,
  row.state,
  row.change_user,
  row.change_time,
  row.draft
  from (
    select *
      from layout.km_post
      where official_row_id = official_id_in
    union all
    select *
      from layout.km_post
      where design_row_id = official_id_in
    union all
    select *
      from layout.km_post
      where id = official_id_in
    union all
    select *
      from layout.km_post
      where official_id_in is null
  ) row
  where case publication_state_in
          when 'OFFICIAL' then not row.draft
          else row.draft
            or case
                 when row.design_id is null then
                   not exists(select *
                                from layout.km_post overriding_draft
                                where overriding_draft.design_id is not distinct from design_id_in
                                  and overriding_draft.draft
                                  and overriding_draft.official_row_id = row.id)
                 else not exists(select *
                                   from layout.km_post overriding_draft
                                   where overriding_draft.design_id is not distinct from design_id_in
                                     and overriding_draft.draft
                                     and overriding_draft.design_row_id = row.id)
               end
        end
    and case
          when design_id_in is null then row.design_id is null
          else design_id_in = row.design_id
            or (row.design_id is null
              and not draft
              and not exists(select *
                               from layout.km_post overriding_design_official
                               where overriding_design_official.design_id = design_id_in
                                 and not overriding_design_official.draft
                                 and overriding_design_official.official_row_id = row.id))
        end
$$;
