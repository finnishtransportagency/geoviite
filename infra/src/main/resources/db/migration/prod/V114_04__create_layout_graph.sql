-- do $$ begin raise exception 'TODO'; end $$;

drop table if exists switch_version_temp;
create temp table switch_version_temp as
select id, layout_context_id, version, switch_structure_id, start_time, end_time
  from (
    select
      id,
      layout_context_id,
      version,
      switch_structure_id,
      change_time as start_time,
      case
        when lead(deleted) over (partition by id, layout_context_id order by version)
          then (lead(change_time + (interval '1 second')) over (partition by id, layout_context_id order by version))
        else lead(change_time) over (partition by id, layout_context_id order by version)
      end as end_time,
      deleted
      from layout.switch_version
  ) tmp
  where tmp.deleted = false;
alter table switch_version_temp add primary key (id, layout_context_id, version);
create index temp_switch_version_index on switch_version_temp (id, layout_context_id, start_time, end_time, version);

drop table if exists node_point_version_temp;
create temporary table node_point_version_temp as (
  with
    -- All alignment segments that have links to switches. These are versioned with alignments and produce nodes at the segment ends
    node_switch_segments as (
      select
        lt.id as location_track_id,
        lt.layout_context_id as location_track_layout_context_id,
        lt.version as location_track_version,
        lt.change_time as location_track_change_time,
        s.switch_id,
        s.switch_start_joint_number,
        s.switch_end_joint_number,
        s.alignment_id,
        s.alignment_version,
        a.segment_count,
        s.segment_index,
        s.start as start_m,
        postgis.st_m(postgis.st_endpoint(sg.geometry)) as length
        from layout.segment_version s
          inner join layout.alignment_version a on a.id = s.alignment_id and a.version = s.alignment_version
          inner join layout.location_track_version lt on a.id = lt.alignment_id and a.version = lt.alignment_version
          inner join layout.segment_geometry sg on sg.id = s.geometry_id
        where s.switch_id is not null
          and (s.switch_start_joint_number is not null or s.switch_end_joint_number is not null)
--           and lt.deleted = false -- TODO: ignore these, right? there wont be any edges/nodes for deleted tracks in the future?
    ),
    -- All potential locations/versions that could produce a node. Note that there will be duplicates here
    node_point as (
      -- Duplicate segments with multiple queries
      -- Segments that have a joint-point in the beginning
      select
        location_track_id,
        location_track_layout_context_id,
        location_track_version,
        location_track_change_time,
        alignment_id,
        alignment_version,
        null as switch_in_id,
        null as switch_in_joint_number,
        case when switch_start_joint_number is not null then switch_id end as switch_out_id,
        switch_start_joint_number as switch_out_joint_number,
        segment_index as start_segment_index,
        case when segment_index = 0 then null else segment_index - 1 end as end_segment_index,
        null::int as boundary_location_track_id,
        null::layout.boundary_type boundary_type
        from node_switch_segments
        where switch_start_joint_number is not null
      union all
      -- Segments that have a joint-point in the end
      select
        location_track_id,
        location_track_layout_context_id,
        location_track_version,
        location_track_change_time,
        alignment_id,
        alignment_version,
        case when switch_end_joint_number is not null then switch_id end as switch_in_id,
        switch_end_joint_number as switch_in_joint_number,
        null as switch_out_id,
        null as switch_out_joint_number,
        case when segment_index = segment_count - 1 then null else segment_index + 1 end as start_segment_index,
        segment_index as end_segment_index,
        null::int as boundary_location_track_id,
        null::layout.boundary_type boundary_type
        from node_switch_segments
        where switch_end_joint_number is not null
      union all
      -- Location track ends must always be nodes and may have topology links in addition to any segment link
      -- Location track topology starts
      select
        lt.id as location_track_id,
        lt.layout_context_id as location_track_layout_context_id,
        lt.version as location_track_version,
        lt.change_time as location_track_change_time,
        lt.alignment_id,
        lt.alignment_version,
        case when lt.topology_start_switch_joint_number is not null then lt.topology_start_switch_id end as switch_in_id,
        lt.topology_start_switch_joint_number as joint_in_joint_number,
        null as switch_out_id,
        null as switch_out_joint_number,
        0 as start_segment_index,
        null as end_segment_index,
        lt.id as boundary_location_track_id,
        'START'::layout.boundary_type boundary_type
        from layout.location_track_version lt
          inner join layout.alignment_version a on a.id = lt.alignment_id and a.version = lt.alignment_version
          inner join layout.segment_version s
                     on s.alignment_id = a.id and s.alignment_version = a.version and s.segment_index = 0
          inner join layout.segment_geometry sg on sg.id = s.geometry_id
      union all
      -- Location track topology ends
      select
        lt.id as location_track_id,
        lt.layout_context_id as location_track_layout_context_id,
        lt.version as location_track_version,
        lt.change_time as location_track_change_time,
        lt.alignment_id,
        lt.alignment_version,
        null as switch_in_id,
        null as switch_in_joint_number,
        case when lt.topology_end_switch_joint_number is not null then lt.topology_end_switch_id end as switch_out_id,
        lt.topology_end_switch_joint_number as switch_out_joint_number,
        null as start_segment_index,
        a.segment_count - 1 as end_segment_index,
        lt.id as boundary_location_track_id,
        'END'::layout.boundary_type boundary_type
        from layout.location_track_version lt
          inner join layout.alignment_version a on a.id = lt.alignment_id and a.version = lt.alignment_version
          inner join layout.segment_version s on s.alignment_id = a.id and s.alignment_version = a.version and
                                                 s.segment_index = a.segment_count - 1
          inner join layout.segment_geometry sg on sg.id = s.geometry_id

    ),
    node_point_enriched as (
      select
        node.*,
        joint_in.role as switch_in_joint_role,
        joint_out.role as switch_out_joint_role
        from node_point node
          -- Join draft version if (and only if) the location track is a draft
          left join switch_version_temp switch_in_d
                    on switch_in_d.id = node.switch_in_id
                      and node.location_track_layout_context_id = 'main_draft'
                      and switch_in_d.layout_context_id = 'main_draft'
                      and switch_in_d.start_time <= node.location_track_change_time
                      and (switch_in_d.end_time is null or switch_in_d.end_time > node.location_track_change_time)
          left join switch_version_temp switch_out_d
                    on switch_out_d.id = node.switch_out_id
                      and node.location_track_layout_context_id = 'main_draft'
                      and switch_out_d.layout_context_id = 'main_draft'
                      and switch_out_d.start_time <= node.location_track_change_time
                      and (switch_out_d.end_time is null or switch_out_d.end_time > node.location_track_change_time)
          -- Join official version regardless of the location track version
          left join switch_version_temp switch_in_o
                    on switch_in_o.id = node.switch_in_id
                      and switch_in_o.layout_context_id = 'main_official'
                      and switch_in_o.start_time <= node.location_track_change_time
                      and (switch_in_o.end_time is null or switch_in_o.end_time > node.location_track_change_time)
          left join switch_version_temp switch_out_o
                    on switch_out_o.id = node.switch_out_id
                      and switch_out_o.layout_context_id = 'main_official'
                      and switch_out_o.start_time <= node.location_track_change_time
                      and (switch_out_o.end_time is null or switch_out_o.end_time > node.location_track_change_time)
          -- Join the correct joint version via coalesce, favoring draft if it exists and is joined
          left join layout.switch_version_joint joint_in
                    on joint_in.switch_id = node.switch_in_id
                      and joint_in.switch_layout_context_id = coalesce(switch_in_d.layout_context_id, switch_in_o.layout_context_id)
                      and joint_in.switch_version = coalesce(switch_in_d.version, switch_in_o.version)
                      and joint_in.number = node.switch_in_joint_number
          left join layout.switch_version_joint joint_out
                    on joint_out.switch_id = node.switch_out_id
                      and joint_out.switch_layout_context_id = coalesce(switch_out_d.layout_context_id, switch_out_o.layout_context_id)
                      and joint_out.switch_version = coalesce(switch_out_d.version, switch_out_o.version)
                      and joint_out.number = node.switch_out_joint_number
    ),
    track_node_version_candidate as (
      select
        location_track_id,
        location_track_layout_context_id,
        location_track_version,
        location_track_change_time,
        alignment_id,
        alignment_version,
        -- Node ordering within the alignment version
        row_number() over (partition by location_track_id, location_track_version, alignment_id, alignment_version order by start_segment_index, end_segment_index)-1 as node_index,
        -- The grouping contracts the duplicated locations to a single node which may have multiple switch links
        -- There can only be one or null of these per node
        (array_agg(distinct switch_in_id) filter (where switch_in_id is not null))[1] as switch_in_id,
        (array_agg(distinct switch_in_joint_number) filter (where switch_in_joint_number is not null))[1] as switch_in_joint_number,
        (array_agg(distinct switch_in_joint_role) filter (where switch_in_joint_role is not null))[1] as switch_in_joint_role,
        (array_agg(distinct switch_out_id) filter (where switch_out_id is not null))[1] as switch_out_id,
        (array_agg(distinct switch_out_joint_number) filter (where switch_out_joint_number is not null))[1] as switch_out_joint_number,
        (array_agg(distinct switch_out_joint_role) filter (where switch_out_joint_role is not null))[1] as switch_out_joint_role,
        (array_agg(distinct boundary_location_track_id) filter (where boundary_location_track_id is not null))[1] as boundary_location_track_id,
        (array_agg(distinct boundary_type) filter (where boundary_location_track_id is not null))[1] as boundary_type,
        start_segment_index,
        end_segment_index
        from node_point_enriched
        group by location_track_id, location_track_layout_context_id, location_track_version, location_track_change_time, alignment_id, alignment_version, start_segment_index, end_segment_index
    ),
    track_node_version_with_ports as (
      select
        location_track_id,
        location_track_layout_context_id,
        location_track_version,
        location_track_change_time,
        alignment_id,
        alignment_version,
        node_index,
        start_segment_index,
        end_segment_index,
        -- tmp.direction as node_track_direction,
        -- Pick the start/end ports for edges by whether the track goes through the node in natural or reversed order
        tmp.node_type,
        tmp.increasing as dir_increasing,
        -- Switch nodes will always have both ports
        -- Even when the other port content is null: only 1 switch joint at that point
        -- When both ports are combined, everything connects to A. Otherwise, resolve correct port by direction
        -- A track boundary would only have both ports if there are two tracks and we don't have that in base data
        (case
           when node_type = 'SWITCH' and tmp.combine_ports then 'A'
           when node_type = 'SWITCH' and tmp.increasing then 'B'
           when node_type = 'SWITCH' and not tmp.increasing then 'A'
           when node_type = 'TRACK_BOUNDARY' and boundary_type = 'START' then 'A'
           else null -- There cannot be a starting edge from a track boundary end
         end)::layout.node_port_type as start_edge_port,
        (case
           when node_type = 'SWITCH' and tmp.combine_ports then 'A'
           when node_type = 'SWITCH' and tmp.increasing then 'A'
           when node_type = 'SWITCH' and not tmp.increasing then 'B'
           when node_type = 'TRACK_BOUNDARY' and boundary_type = 'END' then 'A'
           else null -- There cannot be an ending edge at a track boundary start
         end)::layout.node_port_type as end_edge_port,
        -- The A track boundary is the end of the track in question, if and only if there are no switches for the node
        (case
           when switch_in_id is null and switch_out_id is null then boundary_location_track_id
         end)::int as a_boundary_location_track_id,
        (case
           when switch_in_id is null and switch_out_id is null then boundary_type
         end)::layout.boundary_type as a_boundary_type,
        -- As the track's only link ends up in port A, there is no boundary on port B (the historical data does not hold any)
        null::int as b_boundary_location_track_id,
        null::layout.boundary_type as b_boundary_type,
        -- Any node has 0-2 switches -> assign them to port A/B based on the direction
        case when tmp.increasing then switch_in_id else switch_out_id end as a_switch_id,
        case when tmp.increasing then switch_in_joint_number else switch_out_joint_number end as a_switch_joint_number,
        case when tmp.increasing then switch_in_joint_role else switch_out_joint_role end as a_switch_joint_role,
        case when tmp.combine_ports then null when tmp.increasing then switch_out_id else switch_in_id end as b_switch_id,
        case when tmp.combine_ports then null when tmp.increasing then switch_out_joint_number else switch_in_joint_number end as b_switch_joint_number,
        case when tmp.combine_ports then null when tmp.increasing then switch_out_joint_role else switch_in_joint_role end as b_switch_joint_role
        from track_node_version_candidate,
          lateral (
            select
              (case when switch_in_id is not null or switch_out_id is not null then 'SWITCH' else 'TRACK_BOUNDARY' end)::layout.node_type as node_type,
              (array [switch_in_id, switch_in_joint_number, boundary_location_track_id]
                <= array [switch_out_id, switch_out_joint_number, boundary_location_track_id]
              ) as increasing,
              -- If both ports contain the same switch-joint, they get combined into A and the B port is null
              (switch_in_id = switch_out_id and switch_in_joint_number = switch_out_joint_number) as combine_ports
            ) as tmp
    )
  select
    *,
    layout.calculate_node_port_hash(
        node_type,
        a_switch_id,
        a_switch_joint_number,
        a_switch_joint_role,
        a_boundary_location_track_id,
        a_boundary_type
    ) as a_port_hash,
    case
      -- Switch-nodes always have both ports.
      -- Boundaries can only have the B port if there are two directly connected tracks and that is not the case in the base data
      when node_type = 'SWITCH' then
        layout.calculate_node_port_hash(
            node_type,
            b_switch_id,
            b_switch_joint_number,
            b_switch_joint_role,
            b_boundary_location_track_id,
            b_boundary_type
        )
    end as b_port_hash
  from track_node_version_with_ports
);
alter table node_point_version_temp
  add column node_hash uuid not null generated always as (
    layout.calculate_node_hash(a_port_hash, b_port_hash)
  ) stored;

do $$
  begin
    if exists(
      select * from (
        select
          node_hash,
          count(distinct a_port_hash) distinct_a_ports,
          count(distinct b_port_hash) distinct_b_ports
          from node_point_version_temp
          group by node_hash
      ) tmp
               where distinct_a_ports <> 1 or distinct_b_ports > 1
    ) then
      raise exception 'Node ports definition conflict';
    end if;
  end $$;

-- Create immutable nodes
insert into layout.node (hash, type)
select distinct on (node_hash)
  node_hash as hash,
  node_type as type
  from node_point_version_temp;

-- Insert node ports
insert into layout.node_port
  (node_id, port, hash, switch_id, switch_joint_number, switch_joint_role, boundary_location_track_id, boundary_type)
  select
    node.id as node_id,
    tmp.port,
    tmp.port_hash,
    tmp.switch_id,
    tmp.switch_joint_number,
    tmp.switch_joint_role,
    tmp.boundary_location_track_id,
    tmp.boundary_type
    from (
      select distinct on (node_hash)
        node_hash,
        'A'::layout.node_port_type as port,
        a_port_hash as port_hash,
        a_switch_id as switch_id,
        a_switch_joint_number as switch_joint_number,
        a_switch_joint_role as switch_joint_role,
        a_boundary_location_track_id as boundary_location_track_id,
        a_boundary_type as boundary_type
        from node_point_version_temp
      union all
      select distinct on (node_hash)
        node_hash,
        'B'::layout.node_port_type as port,
        b_port_hash as port_hash,
        b_switch_id as switch_id,
        b_switch_joint_number as switch_joint_number,
        b_switch_joint_role as switch_joint_role,
        b_boundary_location_track_id as boundary_location_track_id,
        b_boundary_type as boundary_type
        from node_point_version_temp
        where b_port_hash is not null
    ) tmp inner join layout.node on node.hash = tmp.node_hash;

-- All potential edges, as seen from the location track versions' point of view
drop table if exists track_edge_version_temp;
create temp table track_edge_version_temp as (
  with edge_version_candidates as (
    select
      node.location_track_id,
      node.location_track_layout_context_id,
      node.location_track_version,
      node.alignment_id,
      node.alignment_version,
      node.node_index as start_node_index,
      lead(node.node_index) over (
        partition by node.location_track_id, node.location_track_layout_context_id, node.location_track_version
        order by node.node_index
      ) as end_node_index,
      node.node_hash as start_node_hash,
      node.start_edge_port as start_node_port,
      lead(node.node_hash) over (
        partition by node.location_track_id, node.location_track_layout_context_id, node.location_track_version
        order by node.node_index
      ) as end_node_hash,
      lead(node.end_edge_port) over (
        partition by node.location_track_id, node.location_track_layout_context_id, node.location_track_version
        order by node.node_index
      ) as end_node_port,
      node.start_segment_index,
      lead(node.end_segment_index) over (
        partition by node.location_track_id, node.location_track_layout_context_id, node.location_track_version
        order by node.node_index
      ) as end_segment_index,
      ltv.change_time,
      ltv.change_user,
      ltv.deleted
      from node_point_version_temp node
        left join layout.location_track_version ltv
                  on ltv.id = node.location_track_id
                    and ltv.layout_context_id = node.location_track_layout_context_id
                    and ltv.version = node.location_track_version
      -- TODO: GVT-2930 - we could not generate track-edges for deleted tracks, as those are identical to the previous version
      -- If we do generate them, we need to also insert them on track deletion, as the reference is turned compared to alignment
  )
  select
    e.*,
    start_node.id start_node_id,
    end_node.id end_node_id
    from edge_version_candidates e
      inner join layout.node start_node on e.start_node_hash = start_node.hash
      inner join layout.node end_node on e.end_node_hash = end_node.hash
);
alter table track_edge_version_temp
  add primary key (location_track_id, location_track_layout_context_id, location_track_version, start_node_index);

drop table if exists edge_temp;
create temporary table edge_temp as (
  with
    edge_candidates as (
      select
        e.location_track_id,
        e.location_track_layout_context_id,
        e.location_track_version,
        e.alignment_id,
        e.alignment_version,
        e.start_node_index as edge_index,
        e.start_segment_index,
        e.end_segment_index,
        e.start_node_id,
        e.start_node_port,
        e.end_node_id,
        e.end_node_port,
        e.change_time,
        e.change_user,
        e.deleted,
        array_agg(
            layout.calculate_segment_hash(
                s.geometry_alignment_id,
                s.geometry_element_index,
                s.source_start::decimal(13, 6),
                s.source,
                s.geometry_id
            ) order by s.segment_index
        ) as segment_hashes
        from track_edge_version_temp e
          left join layout.segment_version s
                    on s.alignment_id = e.alignment_id
                      and s.alignment_version = e.alignment_version
                      and s.segment_index between e.start_segment_index and e.end_segment_index
        group by e.location_track_id, e.location_track_layout_context_id, e.location_track_version, e.start_node_index
    ),
    edge_candidates_with_hash as (
      select
        *,
        layout.calculate_edge_hash(
            start_node_id,
            start_node_port,
            end_node_id,
            end_node_port,
            segment_hashes
        ) as edge_hash
        from edge_candidates
    ),
    edges as (
      select
        -- These form the location-track -> edge linkings. Each array must have the same count of elements for unnest
        -- Notably, the edge index for a particular linking may differ even when multiple location track versions share an edge
        array_agg(location_track_id order by location_track_id, location_track_layout_context_id, location_track_version) as location_track_ids,
        array_agg(location_track_layout_context_id order by location_track_id, location_track_layout_context_id, location_track_version) as location_track_layout_context_ids,
        array_agg(location_track_version order by location_track_id, location_track_layout_context_id, location_track_version) as location_track_versions,
        array_agg(edge_index order by location_track_id, location_track_layout_context_id, location_track_version) as edge_indexes,
        -- The segment contents can be identical on multiple alignment versions. It doesn't matter which one we pick the data from, so long as it's the same one
        -- The version & change time could be picked by min, but we use the same syntax as others for consistency
        (array_agg(alignment_id order by alignment_id, alignment_version))[1] as alignment_id,
        (array_agg(alignment_version order by alignment_id, alignment_version))[1] as alignment_version,
        (array_agg(alignment_id order by alignment_id, alignment_version)) as alignment_ids,
        (array_agg(alignment_version order by alignment_id, alignment_version)) as alignment_versions,
        (array_agg(start_segment_index order by alignment_id, alignment_version))[1] as start_segment_index,
        (array_agg(end_segment_index order by alignment_id, alignment_version))[1] as end_segment_index,
        (array_agg(change_time order by alignment_id, alignment_version))[1] as change_time,
        (array_agg(change_user order by alignment_id, alignment_version))[1] as change_user,
        edge_hash,
        segment_hashes,
        start_node_id,
        start_node_port,
        end_node_id,
        end_node_port
        from edge_candidates_with_hash
        group by edge_hash, start_node_id, start_node_port, end_node_id, end_node_port, segment_hashes
    )
  select *
    from edges
);
alter table edge_temp add primary key (edge_hash);

insert into layout.edge(
  start_node_id,
  start_node_port,
  end_node_id,
  end_node_port,
  bounding_box,
  segment_count,
  length,
  hash
)
select
  e.start_node_id,
  e.start_node_port,
  e.end_node_id,
  e.end_node_port,
  postgis.st_extent(sg.geometry) as bounding_box,
  e.end_segment_index - e.start_segment_index + 1 as segment_count,
  sum(postgis.st_m(postgis.st_endpoint(sg.geometry))) as length,
  e.edge_hash as hash
  from edge_temp e
    left join layout.segment_version sv
              on sv.alignment_id = e.alignment_id
                and sv.alignment_version = e.alignment_version
                and sv.segment_index between e.start_segment_index and e.end_segment_index
    left join layout.segment_geometry sg on sg.id = sv.geometry_id
  group by e.edge_hash;

drop table if exists edge_segment_temp;
create temp table edge_segment_temp as
select
  edge.id edge_id,
  sv.segment_index - e.start_segment_index as segment_index,
  sv.geometry_alignment_id,
  sv.geometry_element_index,
  sv.start - first_sv.start as start,
  sv.source_start,
  sv.source,
  sv.geometry_id,
  e.segment_hashes[sv.segment_index - e.start_segment_index + 1] as hash,
  sv.alignment_id orig_alignment_id,
  sv.alignment_version orig_alignment_version,
  sv.segment_index orig_segment_index
  from edge_temp e
    inner join layout.edge on edge.hash = e.edge_hash
    left join layout.segment_version first_sv
              on first_sv.alignment_id = e.alignment_id
                and first_sv.alignment_version = e.alignment_version
                and first_sv.segment_index = e.start_segment_index
    left join layout.segment_version sv
              on sv.alignment_id = e.alignment_id
                and sv.alignment_version = e.alignment_version
                and sv.segment_index between e.start_segment_index and e.end_segment_index;
alter table edge_segment_temp add primary key (edge_id, segment_index);

insert into layout.edge_segment
  (edge_id, segment_index, geometry_alignment_id, geometry_element_index, start, source_start, source, geometry_id, hash)
select
  edge_id, segment_index, geometry_alignment_id, geometry_element_index, start, source_start, source, geometry_id, hash
  from edge_segment_temp;

insert into layout.initial_edge_segment_metadata (edge_id, segment_index, metadata_id)
select es.edge_id, es.segment_index, md.metadata_id
  from layout.initial_segment_metadata md
    inner join edge_segment_temp es
               on es.orig_alignment_id = md.alignment_id and es.orig_alignment_version = 1 and es.orig_segment_index = md.segment_index;

do $$
  begin
    if exists(
      select * from (
        select
          *,
          layout.calculate_segment_hash(
              geometry_alignment_id,
              geometry_element_index,
              source_start,
              source,
              geometry_id
          ) as recalculated_hash
          from layout.edge_segment
      ) tmp where tmp.hash <> recalculated_hash
    ) then
      raise exception 'Edge segment hash does not match the migrated value';
    end if;
  end $$;

insert into layout.location_track_version_edge
  (
    location_track_id,
    location_track_layout_context_id,
    location_track_version,
    edge_index,
    edge_id,
    start_m
  )
select
  unnest(e.location_track_ids) as location_track_id,
  unnest(e.location_track_layout_context_ids) as location_track_layout_context_id,
  unnest(e.location_track_versions) as location_track_version,
  unnest(e.edge_indexes) as edge_index,
  edge.id as edge_id,
  start_segment.start as start_m
  from edge_temp e
    inner join layout.edge on edge.hash = e.edge_hash
    inner join layout.segment_version start_segment
               on start_segment.alignment_id = e.alignment_id
                 and start_segment.alignment_version = e.alignment_version
                 and start_segment.segment_index = e.start_segment_index;


create temp table track_geometry_verify as
with
  alignment_geoms as (
    select
      ltv.id,
      ltv.layout_context_id,
      ltv.version,
      array_agg(sv.geometry_id order by sv.segment_index) filter (where sv.geometry_id is not null) as geoms,
      array_agg(distinct sv.geometry_id order by sv.geometry_id) filter (where sv.geometry_id is not null) as distinct_geoms
      from layout.location_track_version ltv
        left join layout.alignment_version a on a.id = ltv.alignment_id and a.version = ltv.alignment_version
        left join layout.segment_version sv on sv.alignment_id = a.id and sv.alignment_version = a.version
      group by ltv.id, ltv.layout_context_id, ltv.version
  ),
  edge_geoms as (
    select
      ltv.id,
      ltv.layout_context_id,
      ltv.version,
      array_agg(es.geometry_id order by ltve.edge_index, es.segment_index) filter (where es.geometry_id is not null) geoms,
      array_agg(distinct es.geometry_id order by es.geometry_id) filter (where es.geometry_id is not null) distinct_geoms
      from layout.location_track_version ltv
        left join layout.location_track_version_edge ltve
                  on ltv.id = ltve.location_track_id
                    and ltv.layout_context_id = ltve.location_track_layout_context_id
                    and ltv.version = ltve.location_track_version
        left join layout.edge e on ltve.edge_id = e.id
        left join layout.edge_segment es on e.id = es.edge_id
      group by ltv.id, ltv.layout_context_id, ltv.version
  )
select
  ltv.id,
  ltv.layout_context_id,
  ltv.name,
  a.geoms as alignment_geoms,
  a.distinct_geoms as alignment_distinct_geoms,
  e.geoms as edge_geoms,
  e.distinct_geoms as edge_distinct_geoms
  from layout.location_track_version ltv
    left join alignment_geoms a on ltv.id = a.id and ltv.layout_context_id = a.layout_context_id and ltv.version = a.version
    left join edge_geoms e on ltv.id = e.id and ltv.layout_context_id = e.layout_context_id and ltv.version = e.version;

do $$
  begin
    if exists(
      select *
        from track_geometry_verify
        where alignment_geoms <> edge_geoms
           or alignment_distinct_geoms <> edge_distinct_geoms
           or array_length(alignment_distinct_geoms, 1) <> array_length(alignment_geoms, 1)
           or array_length(edge_distinct_geoms, 1) <> array_length(edge_geoms, 1)
    ) then
      raise exception 'Location track geometries have changed in the migration';
    end if;
  end $$;

create temp table track_switches_verify as
with
  alignment_segment_switches_start as (
    select
      ltv.id,
      ltv.layout_context_id,
      ltv.version,
      concat(switch_id, '_', switch_start_joint_number) switch
      from layout.segment_version
        inner join layout.location_track_version ltv on ltv.alignment_id = segment_version.alignment_id and ltv.alignment_version = segment_version.alignment_version
      where switch_id is not null and switch_start_joint_number is not null
  ),
  alignment_segment_switches_end as (
    select
      lt.id,
      lt.layout_context_id,
      lt.version,
      concat(switch_id, '_', switch_end_joint_number) switch
      from layout.segment_version
        inner join layout.location_track_version lt on lt.alignment_id = segment_version.alignment_id and lt.alignment_version = segment_version.alignment_version
      where switch_id is not null and switch_end_joint_number is not null
  ),
  track_start_switches as (
    select id, layout_context_id, version, concat(topology_start_switch_id, '_', topology_start_switch_joint_number) switch
      from layout.location_track_version
      where topology_start_switch_id is not null and topology_start_switch_joint_number is not null
  ),
  track_end_switches as (
    select id, layout_context_id, version, concat(topology_end_switch_id, '_', topology_end_switch_joint_number) switch
      from layout.location_track_version
      where topology_end_switch_id is not null and topology_end_switch_joint_number is not null
  ),
  alignment_switches as (
    select id, layout_context_id, version, array_agg(distinct switch order by switch) switches
      from (
        select * from alignment_segment_switches_start
        union all
        select * from alignment_segment_switches_end
        union all
        select * from track_start_switches
        union all
        select * from track_end_switches
      ) asdf
      group by id, layout_context_id, version
  ),
  edge_switches_unnest as (
    select distinct
      ltv.id,
      ltv.layout_context_id,
      ltv.version,
      case when np.switch_id is not null then concat(np.switch_id, '_', np.switch_joint_number) end switch
      from layout.location_track_version ltv
        inner join layout.location_track_version_edge ltve
                   on ltve.location_track_id = ltv.id
                     and ltve.location_track_layout_context_id = ltv.layout_context_id
                     and ltve.location_track_version = ltv.version
        inner join layout.edge e on ltve.edge_id = e.id
        left join layout.node_port np on e.end_node_id = np.node_id or e.start_node_id = np.node_id
  ),
  edge_switches as (
    select id, layout_context_id, version, array_agg(switch order by switch) filter (where switch is not null) switches
      from edge_switches_unnest
      group by id, layout_context_id, version
  )
select a.id, a.layout_context_id, a.version, a.switches alignment_switches, e.switches edge_switches
  from alignment_switches a
    left join edge_switches e on a.id = e.id and a.layout_context_id = e.layout_context_id and a.version = e.version;

do $$
  begin
    if exists(
      select *
        from pg_temp.track_switches_verify
        where alignment_switches <> edge_switches
    ) then
      raise exception 'Location track switch links have changed in the migration';
    end if;
  end $$;
