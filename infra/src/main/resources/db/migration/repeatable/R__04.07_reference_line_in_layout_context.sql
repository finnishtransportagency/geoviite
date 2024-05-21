drop function if exists layout.reference_line_in_layout_context(layout.publication_state, int);

create function layout.reference_line_in_layout_context(publication_state_in layout.publication_state, design_id_in int)
  returns table
          (
            row_id            integer,
            official_id       integer,
            draft_id          integer,
            row_version       integer,
            track_number_id   int,
            alignment_id      int,
            alignment_version int,
            start_address     varchar(20),
            draft             boolean,
            change_user       varchar(30),
            change_time       timestamptz,
            bounding_box      postgis.geometry(Polygon, 3067),
            length            numeric(13, 6),
            segment_count     int
          )
  language sql
  stable
as
$$
select
  row.id as row_id,
  coalesce(official_row_id, design_row_id, row.id) as official_id,
  case when row.draft then row.id end as draft_id,
  row.version as row_version,
  row.track_number_id,
  row.alignment_id,
  row.alignment_version,
  row.start_address,
  row.draft,
  row.change_user,
  row.change_time,
  alignment.bounding_box,
  alignment.length,
  alignment.segment_count
  from layout.reference_line row
    left join layout.alignment on row.alignment_id = alignment.id
  where case publication_state_in
          when 'OFFICIAL' then not row.draft
          else row.draft
            or case
                 when row.design_id is null then
                   not exists(select *
                                from layout.reference_line overriding_draft
                                where overriding_draft.design_id is not distinct from design_id_in
                                  and overriding_draft.draft
                                  and overriding_draft.official_row_id = row.id)
                 else not exists(select *
                                   from layout.reference_line overriding_draft
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
                               from layout.reference_line overriding_design_official
                               where overriding_design_official.design_id = design_id_in
                                 and not overriding_design_official.draft
                                 and overriding_design_official.official_row_id = row.id))
        end
$$
