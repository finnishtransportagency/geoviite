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
  where not reference_line.cancelled
    and case publication_state_in
          when 'OFFICIAL' then not reference_line.draft
          else reference_line.draft
            or not exists(select *
                          from layout.reference_line overriding_draft
                          where overriding_draft.design_id is not distinct from design_id_in
                            and overriding_draft.draft
                            and not (reference_line.design_id is null and overriding_draft.cancelled)
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
                                 and overriding_design_official.id = reference_line.id
                                   and (publication_state_in = 'OFFICIAL' or not exists (
                                     select *
                                       from layout.reference_line design_cancellation
                                       where design_cancellation.design_id = design_id_in
                                         and design_cancellation.draft
                                         and design_cancellation.cancelled
                                         and design_cancellation.id = reference_line.id))))
        end
$$;

create function layout.reference_line_in_layout_context(publication_state_in layout.publication_state, design_id_in int)
  returns table
          (
            layout_context_id text,
            id                integer,
            design_id         integer,
            draft             boolean,
            version           integer,
            track_number_id   int,
            alignment_id      int,
            alignment_version int,
            start_address     varchar(20),
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
  row.layout_context_id,
  row.id,
  design_id,
  row.draft,
  row.version,
  row.track_number_id,
  row.alignment_id,
  row.alignment_version,
  row.start_address,
  row.change_user,
  row.change_time,
  alignment.bounding_box,
  alignment.length,
  alignment.segment_count
  from layout.reference_line row
    left join layout.alignment on row.alignment_id = alignment.id,
    layout.reference_line_is_in_layout_context(publication_state_in, design_id_in, row)
$$
