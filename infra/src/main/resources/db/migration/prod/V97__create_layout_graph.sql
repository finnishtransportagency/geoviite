drop table if exists node_point_version_temp;
create temporary table node_point_version_temp as (
  with
    -- All alignment segments that have links to switches. These are versioned with alignments and produce nodes at the segment ends
    node_switch_segments as (
      select
        lt.id as location_track_id,
        lt.version as location_track_version,
        lt.change_time as location_track_change_time,
        s.switch_id,
        s.switch_start_joint_number,
        s.switch_end_joint_number,
        s.alignment_id,
        s.alignment_version,
        a.segment_count,
        s.segment_index,
        sg.geometry,
        s.start as start_m,
        postgis.st_m(postgis.st_endpoint(sg.geometry)) as length
        from layout.segment_version s
          inner join layout.alignment_version a on a.id = s.alignment_id and a.version = s.alignment_version
          inner join layout.location_track_version lt on a.id = lt.alignment_id and a.version = lt.alignment_version
          inner join layout.segment_geometry sg on sg.id = s.geometry_id
        where s.switch_id is not null and (s.switch_start_joint_number is not null or
                                           s.switch_end_joint_number is not null)
    ),
    -- All potential locations/versions that could produce a node. Note that there will be duplicates here
    node_points as (
      -- Duplicate segments with multiple queries
      -- Segments that have a joint-point in the beginning
      select
        location_track_id,
        location_track_version,
        alignment_id,
        alignment_version,
        location_track_change_time,
        switch_id,
        switch_start_joint_number as joint_number,
        segment_index as start_segment_index,
        case when segment_index = 0 then null else segment_index - 1 end as end_segment_index,
        segment_count,
        postgis.st_x(postgis.st_startpoint(geometry)) as x,
        postgis.st_y(postgis.st_startpoint(geometry)) as y,
        start_m as m,
        null::int as start_track_link,
        null::int as end_track_link
        from node_switch_segments
        where switch_start_joint_number is not null
      union all
      -- Segments that have a joint-point in the end
      select
        location_track_id,
        location_track_version,
        alignment_id,
        alignment_version,
        location_track_change_time,
        switch_id,
        switch_end_joint_number as joint_number,
        case when segment_index = segment_count - 1 then null else segment_index + 1 end as start_segment_index,
        segment_index as end_segment_index,
        segment_count,
        postgis.st_x(postgis.st_endpoint(geometry)) as x,
        postgis.st_y(postgis.st_endpoint(geometry)) as y,
        start_m + length as m,
        null::int as start_track_link,
        null::int as end_track_link
        from node_switch_segments
        where switch_end_joint_number is not null
      union all
      -- Location track ends must always be nodes and may have topology links in addition to any segment link
      -- Location track topology starts
      select
        lt.id as location_track_id,
        lt.version as location_track_version,
        lt.alignment_id,
        lt.alignment_version,
        lt.change_time as location_track_change_time,
        lt.topology_start_switch_id as switch_id,
        lt.topology_start_switch_joint_number as joint_number,
        0 as start_segment_index,
        null as end_segment_index,
        a.segment_count,
        postgis.st_x(postgis.st_startpoint(geometry)) as x,
        postgis.st_y(postgis.st_startpoint(geometry)) as y,
        0 as m,
        lt.official_id as start_track_link,
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
        lt.version as location_track_version,
        lt.alignment_id,
        lt.alignment_version,
        lt.change_time as location_track_change_time,
        lt.topology_end_switch_id as switch_id,
        lt.topology_end_switch_joint_number as joint_number,
        null as start_segment_index,
        a.segment_count - 1 as end_segment_index,
        a.segment_count,
        postgis.st_x(postgis.st_endpoint(geometry)) as x,
        postgis.st_y(postgis.st_endpoint(geometry)) as y,
        a.length as m,
        null::int as start_track_link,
        lt.official_id end_track_link
        from layout.location_track_version lt
          inner join layout.alignment_version a on a.id = lt.alignment_id and a.version = lt.alignment_version
          inner join layout.segment_version s on s.alignment_id = a.id and s.alignment_version = a.version and
                                                 s.segment_index = a.segment_count - 1
          inner join layout.segment_geometry sg on sg.id = s.geometry_id

    ),
    -- Potential node versions: each locationtrack_version + alignment_version combo can produce new versions for nodes
    track_node_point_unified as (
      select
        location_track_id,
        location_track_version,
        alignment_id,
        alignment_version,
        location_track_change_time,
        case when switch_id is not null and joint_number is not null then array [ switch_id, joint_number ] end as switch_link,
        switch_id,
        joint_number,
        start_track_link,
        end_track_link,
        array [ x::decimal(13, 6), y::decimal(13, 6) ] as location,
        start_segment_index,
        end_segment_index
        from node_points
    ),
    track_node_version_candidate as (
      select
        location_track_id,
        location_track_version,
        alignment_id,
        alignment_version,
        max(location_track_change_time) change_time,
        -- Node identity comes from alignment identity + location
--         dense_rank() over (order by alignment_id, location) node_id,
--         node_id,
        -- Node ordering within the alignment version
        row_number() over (partition by location_track_id, location_track_version, alignment_id, alignment_version order by start_segment_index, end_segment_index) as node_index,
        -- The grouping contracts the duplicated locations to a single node which may have multiple switch links
--         array_agg(distinct array [switch_id, joint_number]) filter (where joint_number is not null) as switch_links,
        array_agg(distinct switch_link order by switch_link) filter (where switch_link is not null) as switch_links,
        -- Duplicate the data into separate arrays for easy unnesting in later processing
        array_agg(switch_id order by switch_id, joint_number) switch_ids,
        array_agg(joint_number order by switch_id, joint_number) switch_joints,
        -- There can only be one or null of these per node
        min(start_track_link) as start_track_link,
        min(end_track_link) as end_track_link,
--         array_agg(distinct start_track_link order by start_track_link) filter (where start_track_link is not null) as start_track_links,
--         array_agg(distinct end_track_link order by end_track_link) filter (where end_track_link is not null) as end_track_links,
        -- The locations should all be approximately the same, but there might be minor variation due to floating point errors
--         array_agg(distinct array [x::decimal(13, 6), y::decimal(13, 6)]) as locations,
        array_agg(distinct location) as locations,
--         array_agg(distinct array[location_track_id, location_track_version]) as location_track_links,
        start_segment_index,
        end_segment_index
--         from node_points
          from track_node_point_unified
        group by location_track_id, location_track_version, alignment_id, alignment_version, start_segment_index, end_segment_index
    )
    select
      *,
      layout.calculate_node_key(switch_links, start_track_link, end_track_link) as node_key
    from track_node_version_candidate
);

-- select * from node_point_version_temp;

-- Create immutable nodes
insert into layout.node(key) select distinct node_key from node_point_version_temp;
insert into layout.node_switch_joint (node_id, switch_id, switch_joint)
select distinct on (np.node_key)
  node.id as node_id,
  unnest(np.switch_ids) as switch_switch_id,
  unnest(np.switch_joints) as switch_joint
  from node_point_version_temp np
    inner join layout.node on np.node_key = node.key
  where np.switch_links is not null;
insert into layout.node_track_end (node_id, starting_location_track_id, ending_location_track_id)
select distinct on (np.node_key)
  node.id as node_id,
  np.start_track_link,
  np.end_track_link
  from node_point_version_temp np
    inner join layout.node on np.node_key = node.key
  where np.switch_links is null;

drop table if exists track_edge_version_temp;
create temp table track_edge_version_temp as (
  with edge_version_candidates as (
    select
      location_track_id,
      location_track_version,
      alignment_id,
      alignment_version,
      node_index as start_node_index,
      change_time,
      lead(node_index) over (partition by location_track_id, location_track_version, alignment_id, alignment_version order by node_index) as end_node_index,
      node_key as start_node_key,
      lead(node_key) over (partition by location_track_id, location_track_version, alignment_id, alignment_version order by node_index) as end_node_key,
      start_segment_index,
      lead(end_segment_index) over (partition by location_track_id, location_track_version, alignment_id, alignment_version order by node_index) as end_segment_index
      from node_point_version_temp
  )
  select
    e.*,
    start_node.id start_node_id,
    end_node.id end_node_id,
    start_s.start as start_m,
    end_s.start + postgis.st_m(postgis.st_endpoint(end_sg.geometry)) as end_m
    from edge_version_candidates e
      inner join layout.segment_version start_s on e.alignment_id = start_s.alignment_id and e.alignment_version = start_s.alignment_version and e.start_segment_index = start_s.segment_index
      inner join layout.segment_version end_s on e.alignment_id = end_s.alignment_id and e.alignment_version = end_s.alignment_version and e.end_segment_index = end_s.segment_index
      inner join layout.segment_geometry end_sg on end_s.geometry_id = end_sg.id
      inner join layout.node start_node on e.start_node_key = start_node.key
      inner join layout.node end_node on e.end_node_key = end_node.key
);

-- delete from layout.edge where true;
-- Create the immutable edges
insert into layout.edge(start_node, end_node)
select distinct start_node_id, end_node_id
  from track_edge_version_temp;

drop table if exists location_track_edge_version_temp;
create temporary table location_track_edge_version_temp as (
  with edge_version_candidates as (
    select
      e.location_track_id,
      e.location_track_version,
      e.alignment_id,
      e.alignment_version,
      e.start_segment_index,
      e.end_segment_index,
      e.start_node_id,
      e.end_node_id,
      e.change_time,
--       array_agg(row (s.alignment_id, s.alignment_version, s.segment_index)) as old_segment_link,
      array_agg(
          row (s.geometry_alignment_id, s.geometry_element_index, s.start, s.source_start, s.source, s.geometry_id)
          order by s.segment_index) as segment_data
      from track_edge_version_temp e
        left join layout.segment_version s
                  on s.alignment_id = e.alignment_id
                    and s.alignment_version = e.alignment_version
                    and s.segment_index between e.start_segment_index and e.end_segment_index
      -- TODO: all e-fields are singled out: this could be simplified if the temp table had a primary key
      group by e.location_track_id, e.location_track_version, e.alignment_id, e.alignment_version, e.start_node_id,
        e.end_node_id, e.change_time, start_segment_index, e.end_segment_index
  ),
-- Generate a grouping number that will allow us to contract identical consecutive versions into one
    edge_version_candidates_grouped as (
      select
        e.*,
            row_number()
            over (partition by e.alignment_id, e.start_node_id, e.end_node_id order by e.change_time, e.alignment_version, e.location_track_id, e.location_track_version) as full_version,
            row_number()
            over (partition by e.alignment_id, e.start_node_id, e.end_node_id, segment_data order by e.change_time, e.alignment_version, e.location_track_id, e.location_track_version) as group_version
        from edge_version_candidates e
    ),
--       select
--         row (location_track_id, location_track_version) as location_track_links,
--         alignment_id,
--         change_time as change_time,
--         full_version as full_version,
--         start_node_id,
--         end_node_id,
--         segment_data, (full_version-group_version)
--         from edge_version_candidates_grouped
--         order by alignment_id, start_node_id, end_node_id, (full_version-group_version);
    location_track_edge_versions as (
      select
--         array_agg(row (location_track_id, location_track_version)) as location_track_links,
        array_agg(location_track_id order by location_track_id, location_track_version) as location_track_ids,
        array_agg(location_track_version order by location_track_id, location_track_version) as location_track_versions,
        alignment_id,
        min(alignment_version) as alignment_version,
        min(start_segment_index) as start_segment_index,
        max(end_segment_index) as end_segment_index,
        min(change_time) as change_time,
        min(full_version) as full_version,
--       row_number() over (partition by (full_version - group_version), alignment_id, start_node_id, end_node_id, segment_data order by full_version) as version,
        start_node_id,
        end_node_id,
        segment_data
        from edge_version_candidates_grouped
        group by (full_version - group_version), alignment_id, start_node_id, end_node_id, segment_data
    )
  select
    v.location_track_ids,
    v.location_track_versions,
    v.alignment_id,
    v.alignment_version,
    v.start_segment_index,
    v.end_segment_index,
    v.change_time,
    row_number() over (partition by v.alignment_id, edge.id order by full_version) as version,
    edge.id as edge_id
    from location_track_edge_versions v
      left join layout.edge on edge.start_node = v.start_node_id and edge.end_node = v.end_node_id
--   order by e.location_track_id, e.location_track_version, e.alignment_id, e.alignment_version, s.segment_index
);
-- select * from location_track_edge_version_temp;

-- Generate a grouping number that will allow us to contract identical consecutive versions into one
-- Partitioning:
-- * Each alignment has their own nodes, and each location marks a node identity within it
-- * The index may vary without the node changing, so identity can't be based on that
-- * The varying data in a node is the switch links, so group numbering (marking that version differs from previous) is based on that
-- * Note: this does not create deleted versions or produce new nodes for the same location
row_number()
          over (partition by alignment_id, locations order by change_time, alignment_version, location_track_id, location_track_version)
               - row_number()
            over (partition by alignment_id, locations, switch_links order by change_time, alignment_version, location_track_id, location_track_version) as grp
--           ,
--         row_number() over (partition by alignment_id, node_index order by change_time, alignment_version)
--       - row_number() over (partition by alignment_id, node_index, switch_links, locations order by change_time, alignment_version) grp2


-- alter table edge_version_temp add primary key (alignment_id, alignment_version, start_node_index);
drop index if exists temp_edge_version_index;
create index temp_edge_version_index
  on edge_version_temp (alignment_id, alignment_version, start_segment_index, end_segment_index);
drop index if exists temp_edge_version_node_index;
create index temp_edge_version_node_index
  on edge_version_temp (alignment_id, alignment_version, start_node_index);

select * from edge_version_temp where alignment_id = 5246;

insert into layout.edge_version(alignment_id, alignment_version, start_node_index, bounding_box, segment_count, length)
  select
   e.alignment_id,
   e.alignment_version,
   e.start_node_index,
--     array_agg(sg.bounding_box) as bounding_box,
   postgis.st_extent(sg.bounding_box) as bounding_box,
   e.end_segment_index - e.start_segment_index as segment_count,
--    sum(postgis.st_length(sg.geometry)) as length,
   e.end_m - e.start_m as length
--    sum(postgis.st_m(postgis.st_endpoint(sg.geometry))) as length2
--    end_node_index,
--    start_segment_index,
--    end_segment_index
   from edge_version_temp e
     left join layout.segment_version sv on sv.alignment_id = e.alignment_id and sv.alignment_version = e.alignment_version and sv.segment_index between e.start_segment_index and e.end_segment_index
     left join layout.segment_geometry sg on sg.id = sv.geometry_id
   group by e.alignment_id, e.alignment_version, e.start_node_index, e.start_segment_index, e.end_segment_index, e.end_m, e.start_m
;

select
  e.alignment_id,
  e.alignment_version,
  e.start_node_index,
  sv.segment_index - e.start_segment_index as segment_index,
  sv.geometry_alignment_id,
  sv.geometry_element_index,
  sv.start - e.start_m,
  sv.source_start,
  sv.source,
  sv.geometry_id
--     array_agg(sg.bounding_box) as bounding_box,
--   postgis.st_extent(sg.bounding_box) as bounding_box,
--   count(*) as segment_count,
--    sum(postgis.st_length(sg.geometry)) as length,
--   sum(postgis.st_m(postgis.st_endpoint(sg.geometry))) as length2
--    end_node_index,
--    start_segment_index,
--    end_segment_index
  from edge_version_temp e
    left join layout.segment_version sv on sv.alignment_id = e.alignment_id and sv.alignment_version = e.alignment_version and sv.segment_index between e.start_segment_index and e.end_segment_index

-- select * from layout.edge_version where alignment_id = 261;
