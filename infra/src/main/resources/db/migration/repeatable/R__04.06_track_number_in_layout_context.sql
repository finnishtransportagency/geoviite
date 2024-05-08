drop function if exists layout.track_number_in_layout_context(layout.publication_state, int);

create function layout.track_number_in_layout_context(publication_state_in layout.publication_state, design_id_in int)
  returns table
          (
            row_id      integer,
            official_id integer,
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
  case when row.draft then row.id end as draft_id,
  row.version as row_version,
  row.external_id,
  row.number,
  row.description,
  row.state,
  row.draft,
  row.change_user,
  row.change_time
  from layout.track_number row
  where case publication_state_in
          when 'OFFICIAL' then not row.draft
          else row.draft
            or not exists(select *
                            from layout.track_number overriding_draft
                            where overriding_draft.design_id is not distinct from design_id_in
                              and overriding_draft.draft
                              and row.id = case
                                             when design_id_in is null then overriding_draft.official_row_id
                                             else overriding_draft.design_row_id
                                           end)
        end
    and case
          when design_id_in is null then row.design_id is null
          else design_id_in = row.design_id
            or (row.design_id is null
              and not draft
              and not exists(select *
                               from layout.track_number overriding_design_official
                               where overriding_design_official.design_id = design_id_in
                                 and not overriding_design_official.draft
                                 and overriding_design_official.official_row_id = row.id))
        end
$$;
