drop table if exists node_point_version_temp;
-- create temporary table node_point_version_temp as (
  with
    -- All alignment segments that have links to switches. These are versioned with alignments and produce nodes at the segment ends
    node_switch_segments as (
      select
        lt.id as location_track_id,
        lt.version as location_track_version,
        a.change_time as alignment_change_time,
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
        alignment_change_time as change_time, -- segment links change with alignment
        switch_id,
        switch_start_joint_number as joint_number,
        segment_index as start_segment_index,
        case when segment_index = 0 then null else segment_index - 1 end as end_segment_index,
        segment_count,
        postgis.st_x(postgis.st_startpoint(geometry)) as x,
        postgis.st_y(postgis.st_startpoint(geometry)) as y,
        start_m as m
        from node_switch_segments
        where switch_start_joint_number is not null
      union all
      -- Segments that have a joint-point in the end
      select
        location_track_id,
        location_track_version,
        alignment_id,
        alignment_version,
        alignment_change_time as change_time, -- segment links change with alignment
        switch_id,
        switch_end_joint_number as joint_number,
        case when segment_index = segment_count - 1 then null else segment_index + 1 end as start_segment_index,
        segment_index as end_segment_index,
        segment_count,
        postgis.st_x(postgis.st_endpoint(geometry)) as x,
        postgis.st_y(postgis.st_endpoint(geometry)) as y,
        start_m + length as m
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
        lt.change_time as change_time, -- topology links change with the locationtrack
        lt.topology_start_switch_id as switch_id,
        lt.topology_start_switch_joint_number as joint_number,
        0 as start_segment_index,
        null as end_segment_index,
        a.segment_count,
        postgis.st_x(postgis.st_startpoint(geometry)) as x,
        postgis.st_y(postgis.st_startpoint(geometry)) as y,
        0 as m
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
        lt.change_time as change_time, -- topology links change with the locationtrack
        lt.topology_end_switch_id as switch_id,
        lt.topology_end_switch_joint_number as joint_number,
        null as start_segment_index,
        a.segment_count - 1 as end_segment_index,
        a.segment_count,
        postgis.st_x(postgis.st_endpoint(geometry)) as x,
        postgis.st_y(postgis.st_endpoint(geometry)) as y,
        a.length as m
        from layout.location_track_version lt
          inner join layout.alignment_version a on a.id = lt.alignment_id and a.version = lt.alignment_version
          inner join layout.segment_version s on s.alignment_id = a.id and s.alignment_version = a.version and
                                                 s.segment_index = a.segment_count - 1
          inner join layout.segment_geometry sg on sg.id = s.geometry_id

    ),
    -- Potential node versions: each locationtrack_version + alignment_version combo can produce new versions for nodes
    node_point_unified as (
      select
        location_track_id,
        location_track_version,
        alignment_id,
        alignment_version,
        -- Node identity comes from alignment identity + location
        dense_rank() over (order by alignment_id, x::decimal(13, 6), y::decimal(13, 6)) node_id,
        change_time,
        case when joint_number is not null then array [switch_id, joint_number] end as switch_link,
        array [x::decimal(13, 6), y::decimal(13, 6)] as location,
        start_segment_index,
        end_segment_index
        from node_points
    ),
    node_version_candidate as (
      select
        location_track_id,
        location_track_version,
        alignment_id,
        alignment_version,
        change_time,
        -- Node identity comes from alignment identity + location
--         dense_rank() over (order by alignment_id, location) node_id,
        node_id,
        -- Node ordering within the alignment version
        row_number() over (partition by location_track_id, location_track_version, alignment_id, alignment_version order by start_segment_index, end_segment_index) as node_index,
        -- The grouping contracts the duplicated locations to a single node which may have multiple switch links
--         array_agg(distinct array [switch_id, joint_number]) filter (where joint_number is not null) as switch_links,
        array_agg(distinct switch_link) filter (where switch_link is not null) as switch_links,
        -- The locations should all be approximately the same, but there might be minor variation due to floating point errors
--         array_agg(distinct array [x::decimal(13, 6), y::decimal(13, 6)]) as locations,
        array_agg(distinct location) as locations,
--         array_agg(distinct array[location_track_id, location_track_version]) as location_track_links,
        start_segment_index,
        end_segment_index
--         from node_points
          from node_point_unified
        group by location_track_id, location_track_version, alignment_id, alignment_version, node_id, change_time, start_segment_index, end_segment_index, location
    )
       select * from node_version_candidate;
    ,
    node_version_candidate_grouped as (
      select
        location_track_id,
        location_track_version,
        alignment_id,
        alignment_version,
        change_time,
        node_index,
        switch_links,
--         location_track_links,
        locations,
        start_segment_index,
        end_segment_index,
        -- Generate a grouping number that will allow us to contract identical consecutive versions into one
        -- Partitioning:
        -- * Each alignment has their own nodes, and each location marks a node identity within it
        -- * The index may vary without the node changing, so identity can't be based on that
        -- * The varying data in a node is the switch links, so group numbering (marking that version differs from previous) is based on that
        -- * Note: this does not create deleted versions or produce new nodes for the same location
            row_number()
            over (partition by node_id order by change_time, alignment_version, location_track_id, location_track_version)
          - row_number()
            over (partition by node_id, switch_links order by change_time, alignment_version, location_track_id, location_track_version) as grp2,
            row_number()
            over (partition by alignment_id, locations order by change_time, alignment_version, location_track_id, location_track_version)
          - row_number()
            over (partition by alignment_id, locations, switch_links order by change_time, alignment_version, location_track_id, location_track_version) as grp
      --           ,
--         row_number() over (partition by alignment_id, node_index order by change_time, alignment_version)
--       - row_number() over (partition by alignment_id, node_index, switch_links, locations order by change_time, alignment_version) grp2
        from node_version_candidate
    )
  select
    count(*) filter (where grp > 0) grp_count,
    count(*) filter (where grp2 > 0) grp2_count
   from node_version_candidate_grouped;
--    where grp > 0
-- --    where array_length(locations, 1) > 1
-- --    order by alignment_id, node_index, change_time, alignment_version
--    ;
      ,
    node_versions as (
      select
        -- Multiple location track versions can use the same node
        array_agg(distinct array [location_track_id, location_track_version]) as location_track_links,
        alignment_id,
        array_agg(distinct array [alignment_version, node_index, start_segment_index, end_segment_index]) as alignment_links,
        --         array_agg(node_index) as node_indices,
        --       array_agg(distinct start_segment_index) as start_segment_index,
--       array_agg(distinct end_segment_index) as end_segment_index,
        locations,
        switch_links
        from node_version_candidate_grouped
        group by grp, alignment_id, locations, switch_links
    )
  select
    *,
    row_number() over (order by alignment_id, alignment_links, location_track_links) as id
  from node_versions
);
-- select * from node_point_version_temp
-- where array_length(locations, 1) > 1
-- ;
-- TODO: Assert sane assumptions from the temp-table: node location should be unique, each switch linked only once, etc.
do $$
  begin
    if exists(select * from node_point_version_temp where array_length(locations, 1) > 1) then
      raise exception 'Location track node locations are not distinct';
    end if;
  end $$;

insert into layout.location_track_node_version
  (id, location, version, change_user, change_time, deleted)
  select
    id, lo
    from node_point_version_temp;

--            where array_length(locations, 1) > 1
--    where array_length(alignment_versions, 1) <> 1
--              where array_length(alignment_versions, 1) <> array_length(start_segment_index, 1)
--   order by location_track_id, node_index, location_track_version
--   where location_track_id = 4986--alignment_id = 5246
--     )
--   select
--     location_track_id,
--     location_track_version,
--     alignment_id,
--     alignment_version,
--     row_number() over (partition by location_track_id, location_track_version, alignment_id, alignment_version order by start_segment_index, end_segment_index) as node_index,
--     array_agg(distinct array[ switch_id, joint_number ]) filter (where joint_number is not null) switch_links,
--     array_agg(distinct location) locations,
--     start_segment_index,
--     end_segment_index
--     from track_version_node_grouped
--     group by location_track_id, node_index, grp
-- ;

--   select
--     location_track_id,
--     location_track_version,
--     alignment_id,
--     alignment_version,
--     row_number() over (partition by location_track_id, location_track_version, alignment_id, alignment_version order by start_segment_index, end_segment_index) as node_index,
--     array_agg(distinct array[ switch_id, joint_number ]) filter (where joint_number is not null) switch_links,
--     array_agg(distinct location) locations,
--     start_segment_index,
--     end_segment_index
--     from node_points
--     group by location_track_id, location_track_version, alignment_id, alignment_version, start_segment_index, end_segment_index
--     order by location_track_id, location_track_version, node_index
) ;
drop index if exists temp_node_point_version_index;
create index temp_node_point_version_index
  on node_point_version_temp (alignment_id, alignment_version, node_index);


select * from node_point_version_temp where alignment_id = 5246;

-- delete from layout.node_version where true;
insert into layout.node_version(alignment_id, alignment_version, node_index)
select
  alignment_id,
  alignment_version,
  node_index
  from node_point_version_temp
  group by alignment_id, alignment_version, node_index
;

-- select * from layout.node_version where alignment_id = 5246;

drop table if exists edge_version_temp;
create temp table edge_version_temp as (
  with edges as (
    select
      alignment_id,
      alignment_version,
      node_index as start_node_index,
      lead(node_index) over (partition by alignment_id, alignment_version order by node_index) as end_node_index,
      start_segment_index,
      lead(end_segment_index) over (partition by alignment_id, alignment_version order by node_index) as end_segment_index
      from node_point_version_temp
      where alignment_id = 5246
  )
  select
    e.*,
    start_s.start as start_m,
    end_s.start + postgis.st_m(postgis.st_endpoint(end_sg.geometry)) as end_m
    from edges e
      inner join layout.segment_version start_s on e.alignment_id = start_s.alignment_id and e.alignment_version = start_s.alignment_version and e.start_segment_index = start_s.segment_index
      inner join layout.segment_version end_s on e.alignment_id = end_s.alignment_id and e.alignment_version = end_s.alignment_version and e.end_segment_index = end_s.segment_index
      inner join layout.segment_geometry end_sg on end_s.geometry_id = end_sg.id
    where start_segment_index is not null and end_segment_index is not null
);
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
