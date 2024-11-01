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
            or case
                 when track_number.design_id is null then
                   not exists(select *
                                from layout.track_number overriding_draft
                                where overriding_draft.design_id is not distinct from design_id_in
                                  and overriding_draft.draft
                                  and not overriding_draft.cancelled
                                  and overriding_draft.official_row_id = track_number.id)
                 else not exists(select *
                                   from layout.track_number overriding_draft
                                   where overriding_draft.design_id is not distinct from design_id_in
                                     and overriding_draft.draft
                                     and overriding_draft.design_row_id = track_number.id)
               end
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
                                 and overriding_design_official.official_row_id = track_number.id
                                 and (publication_state_in = 'OFFICIAL' or not exists (
                                   select *
                                     from layout.track_number design_cancellation
                                     where design_cancellation.design_id = design_id_in
                                       and design_cancellation.draft
                                       and design_cancellation.cancelled
                                       and design_cancellation.official_row_id = track_number.id))))
        end
$$;

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
  row.official_id,
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
  ) row, layout.track_number_is_in_layout_context(publication_state_in, design_id_in, row)
$$;
