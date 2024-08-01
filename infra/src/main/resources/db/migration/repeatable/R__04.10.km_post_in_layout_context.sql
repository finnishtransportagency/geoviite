drop function if exists layout.km_post_in_layout_context(layout.publication_state, int);
drop function if exists layout.km_post_in_layout_context(layout.publication_state, int, int);

drop function if exists layout.km_post_is_in_layout_context(layout.publication_state, int, layout.km_post);

create function layout.km_post_is_in_layout_context(publication_state_in layout.publication_state, design_id_in int,
                                                    row_in layout.km_post) returns boolean
  language sql as
$$
select
    case publication_state_in
      when 'OFFICIAL' then not row_in.draft
      else row_in.draft
        or case
             when row_in.design_id is null then
               not exists(select *
                            from layout.km_post overriding_draft
                            where overriding_draft.design_id is not distinct from design_id_in
                              and overriding_draft.draft
                              and overriding_draft.official_row_id = row_in.id)
             else not exists(select *
                               from layout.km_post overriding_draft
                               where overriding_draft.design_id is not distinct from design_id_in
                                 and overriding_draft.draft
                                 and overriding_draft.design_row_id = row_in.id)
           end
    end
    and case
          when design_id_in is null then row_in.design_id is null
          else design_id_in = row_in.design_id
            or (row_in.design_id is null
              and not row_in.draft
              and not exists(select *
                               from layout.km_post overriding_design_official
                               where overriding_design_official.design_id = design_id_in
                                 and not overriding_design_official.draft
                                 and overriding_design_official.official_row_id = row_in.id))
        end
$$ stable;

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
            location            postgis.geometry(Point, 3067),
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
  row.location,
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
  where layout.km_post_is_in_layout_context(publication_state_in, design_id_in, row);
$$;
