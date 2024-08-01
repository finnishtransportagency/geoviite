drop function if exists layout.track_number_in_layout_context(layout.publication_state, int);
drop function if exists layout.track_number_in_layout_context(layout.publication_state, int, int);

drop function if exists layout.track_number_is_in_layout_context(layout.publication_state, int, layout.track_number);

create function layout.track_number_is_in_layout_context(publication_state_in layout.publication_state,
                                                         design_id_in int,
                                                         row_in layout.track_number) returns boolean
  language sql as
$$
select
    case publication_state_in
      when 'OFFICIAL' then not row_in.draft
      else row_in.draft
        or case
             when row_in.design_id is null then
               not exists(select *
                            from layout.track_number overriding_draft
                            where overriding_draft.design_id is not distinct from design_id_in
                              and overriding_draft.draft
                              and overriding_draft.official_row_id = row_in.id)
             else not exists(select *
                               from layout.track_number overriding_draft
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
                               from layout.track_number overriding_design_official
                               where overriding_design_official.design_id = design_id_in
                                 and not overriding_design_official.draft
                                 and overriding_design_official.official_row_id = row_in.id))
        end
$$ stable;

create function layout.track_number_in_layout_context(publication_state_in layout.publication_state, design_id_in int,
                                                      official_id_in int default null)
  returns table
          (
            row_id      integer,
            official_id integer,
            design_id   integer,
            draft_id    integer,
            row_version integer,
            external_id varchar(50),
            number      varchar(30),
            description varchar(100),
            state       layout.state,
            draft       boolean,
            change_user varchar(30),
            change_time timestamptz
          )
  language sql
  stable
as
$$
select
  row.id as row_id,
  coalesce(row.official_row_id, row.design_row_id, row.id) as official_id,
  design_id,
  case when row.draft then row.id end as draft_id,
  row.version as row_version,
  row.external_id,
  row.number,
  row.description,
  row.state,
  row.draft,
  row.change_user,
  row.change_time
  from (
    select *
      from layout.track_number
      where official_row_id = official_id_in
    union all
    select *
      from layout.track_number
      where design_row_id = official_id_in
    union all
    select *
      from layout.track_number
      where id = official_id_in
    union all
    select *
      from layout.track_number
      where official_id_in is null
  ) row
  where layout.track_number_is_in_layout_context(publication_state_in, design_id_in, row);
$$;
