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
          and lt.deleted = false -- TODO: ignore these, right? there wont be any edges/nodes for deleted tracks in the future?
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
        switch_id switch_out_id,
        switch_start_joint_number as switch_out_joint_number,
        segment_index as start_segment_index,
        case when segment_index = 0 then null else segment_index - 1 end as end_segment_index,
        null::int as start_track_link,
        null::int as end_track_link
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
        switch_id as switch_in_id,
        switch_end_joint_number as switch_in_joint_number,
        null as switch_out_id,
        null as switch_out_joint_number,
        case when segment_index = segment_count - 1 then null else segment_index + 1 end as start_segment_index,
        segment_index as end_segment_index,
        null::int as start_track_link,
        null::int as end_track_link
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
        lt.topology_start_switch_id as switch_in_id,
        lt.topology_start_switch_joint_number as joint_in_joint_number,
        null as switch_out_id,
        null as switch_out_joint_number,
        0 as start_segment_index,
        null as end_segment_index,
        lt.id as start_track_link,
        null::int as end_track_link
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
        lt.topology_end_switch_id as switch_out_id,
        lt.topology_end_switch_joint_number as switch_out_joint_number,
        null as start_segment_index,
        a.segment_count - 1 as end_segment_index,
        null::int as start_track_link,
        lt.id end_track_link
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
        row_number() over (partition by location_track_id, location_track_version, alignment_id, alignment_version order by start_segment_index, end_segment_index) as node_index,
        -- The grouping contracts the duplicated locations to a single node which may have multiple switch links
        -- There can only be one or null of these per node
        (array_agg(distinct switch_in_id) filter (where switch_in_id is not null))[1] as switch_in_id,
        (array_agg(distinct switch_in_joint_number) filter (where switch_in_joint_number is not null))[1] as switch_in_joint_number,
        (array_agg(distinct switch_in_joint_role) filter (where switch_in_joint_role is not null))[1] as switch_in_joint_role,
        (array_agg(distinct switch_out_id) filter (where switch_out_id is not null))[1] as switch_out_id,
        (array_agg(distinct switch_out_joint_number) filter (where switch_out_joint_number is not null))[1] as switch_out_joint_number,
        (array_agg(distinct switch_out_joint_role) filter (where switch_out_joint_role is not null))[1] as switch_out_joint_role,
        (array_agg(distinct start_track_link) filter (where start_track_link is not null))[1] as start_track_link,
        (array_agg(distinct end_track_link) filter (where end_track_link is not null))[1] as end_track_link,
        -- The locations should all be approximately the same, but there might be minor variation due to floating point errors
        start_segment_index,
        end_segment_index
        from node_point_enriched
        group by location_track_id, location_track_layout_context_id, location_track_version, location_track_change_time, alignment_id, alignment_version, start_segment_index, end_segment_index
    )
    select
      location_track_id,
      location_track_layout_context_id,
      location_track_version,
      location_track_change_time,
      alignment_id,
      alignment_version,
      node_index,
      switch_in_id,
      switch_in_joint_number,
      switch_in_joint_role,
      switch_out_id,
      switch_out_joint_number,
      switch_out_joint_role,
      case when switch_in_id is null and switch_out_id is null then start_track_link end as start_track_link,
      case when switch_in_id is null and switch_out_id is null then end_track_link end as end_track_link,
      start_segment_index,
      end_segment_index,
      layout.calculate_node_hash(
          switch_in_id,
          switch_in_joint_number,
          switch_in_joint_role,
          switch_out_id,
          switch_out_joint_number,
          switch_out_joint_role,
          start_track_link,
          end_track_link
      ) as node_hash
    from track_node_version_candidate
);

-- Create immutable nodes
insert into layout.node (
  hash,
  switch_in_id,
  switch_in_joint_number,
  switch_in_joint_role,
  switch_out_id,
  switch_out_joint_number,
  switch_out_joint_role,
  starting_location_track_id,
  ending_location_track_id
) select distinct on (node_hash)
  node_hash as hash,
  switch_in_id,
  switch_in_joint_number,
  switch_in_joint_role,
  switch_out_id,
  switch_out_joint_number,
  switch_out_joint_role,
  start_track_link,
  end_track_link
  from node_point_version_temp
on conflict (hash) do nothing;

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
      lead(node.node_hash) over (
        partition by node.location_track_id, node.location_track_layout_context_id, node.location_track_version
        order by node.node_index
      ) as end_node_hash,
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
        e.end_node_id,
        e.change_time,
        e.change_user,
        e.deleted,
        array_agg(
            layout.calculate_segment_hash(
                s.geometry_alignment_id,
                s.geometry_element_index,
                s.source_start,
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
      select *,
        layout.calculate_edge_hash(start_node_id, end_node_id, segment_hashes) as edge_hash
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
        (array_agg(start_segment_index order by alignment_id, alignment_version))[1] as start_segment_index,
        (array_agg(end_segment_index order by alignment_id, alignment_version))[1] as end_segment_index,
        (array_agg(change_time order by alignment_id, alignment_version))[1] as change_time,
        (array_agg(change_user order by alignment_id, alignment_version))[1] as change_user,
        edge_hash,
        start_node_id,
        end_node_id
        from edge_candidates_with_hash
        group by edge_hash, start_node_id, end_node_id
    )
  select *
    from edges
);
alter table edge_temp add primary key (edge_hash);

insert into layout.edge(
  start_node_id,
  end_node_id,
  bounding_box,
  segment_count,
  length,
  hash
)
select
  e.start_node_id,
  e.end_node_id,
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

insert into layout.edge_segment
  (
    edge_id,
    segment_index,
    geometry_alignment_id,
    geometry_element_index,
    start,
    source_start,
    source,
    geometry_id
  )
select
  edge.id,
  sv.segment_index - e.start_segment_index as segment_index,
  sv.geometry_alignment_id,
  sv.geometry_element_index,
  sv.start - first_sv.start,
  sv.source_start,
  sv.source,
  sv.geometry_id
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

-- TODO: Drop these columns instead, but keep them for now to maintain the data for comparison
alter table layout.location_track_version alter column alignment_id drop not null;
alter table layout.location_track_version alter column alignment_version drop not null;
alter table layout.location_track alter column alignment_id drop not null;
alter table layout.location_track alter column alignment_version drop not null;
