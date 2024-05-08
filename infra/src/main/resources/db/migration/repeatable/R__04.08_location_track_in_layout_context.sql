drop function if exists layout.location_track_in_layout_context(layout.publication_state, int);

create function layout.location_track_in_layout_context(publication_state_in layout.publication_state, design_id_in int)
  returns table
          (
            row_id                             integer,
            official_id                        integer,
            draft_id                           integer,
            row_version                        integer,
            alignment_id                       integer,
            alignment_version                  integer,
            track_number_id                    integer,
            external_id                        varchar(50),
            name                               varchar(50),
            description_base                   varchar(256),
            description_suffix                 varchar,
            type                               layout.track_type,
            state                              layout.location_track_state,
            draft                              boolean,
            duplicate_of_location_track_id     integer,
            topological_connectivity           layout.track_topological_connectivity_type,
            topology_start_switch_id           integer,
            topology_start_switch_joint_number integer,
            topology_end_switch_id             integer,
            topology_end_switch_joint_number   integer,
            change_user                        varchar(30),
            change_time                        timestamptz,
            bounding_box                       postgis.geometry(Polygon, 3067),
            length                             numeric(13, 6),
            segment_count                      integer
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
  row.alignment_id,
  row.alignment_version,
  row.track_number_id,
  row.external_id,
  row.name,
  row.description_base,
  row.description_suffix,
  row.type,
  row.state,
  row.draft,
  row.duplicate_of_location_track_id,
  row.topological_connectivity,
  row.topology_start_switch_id,
  row.topology_start_switch_joint_number,
  row.topology_end_switch_id,
  row.topology_end_switch_joint_number,
  row.change_user,
  row.change_time,
  alignment.bounding_box,
  alignment.length,
  alignment.segment_count
  from layout.location_track row
    left join layout.alignment on row.alignment_id = alignment.id
  where case publication_state_in
          when 'OFFICIAL' then not row.draft
          else row.draft
            or not exists(select *
                            from layout.location_track overriding_draft
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
                               from layout.location_track overriding_design_official
                               where overriding_design_official.design_id = design_id_in
                                 and not overriding_design_official.draft
                                 and overriding_design_official.official_row_id = row.id))
        end
$$;
