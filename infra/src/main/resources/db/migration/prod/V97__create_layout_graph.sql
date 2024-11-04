set session geoviite.edit_user to 'MANUAL';
drop table if exists node_point_version;
create temporary table node_point_version as (
  with
    node_segments as (
      select
        lt.id as location_track_id,
        lt.name as location_track_name,
        lt.version as location_track_version,
        s.switch_id,
        s.switch_start_joint_number,
        s.switch_end_joint_number,
        s.alignment_id,
        s.alignment_version,
        a.change_time alignment_change_time,
        a.segment_count,
        s.segment_index,
        sg.geometry,
        (s.segment_index = 0 or s.switch_start_joint_number is not null) as start_is_node,
        (s.segment_index = a.segment_count -1 or s.switch_end_joint_number is not null) as end_is_node
        from layout.segment_version s
          inner join layout.alignment_version a on a.id = s.alignment_id and a.version = s.alignment_version
          inner join layout.location_track_version lt on a.id = lt.alignment_id and a.version = lt.alignment_version
          inner join layout.segment_geometry sg on sg.id = s.geometry_id
        where (s.switch_id is not null and (s.switch_start_joint_number is not null or s.switch_end_joint_number is not null))
           or (s.segment_index = 0)
           or (s.segment_index = a.segment_count - 1)
    ),
    node_points as (
      select
        location_track_id,
        location_track_version,
        location_track_name,
        switch_id,
        switch_start_joint_number as joint_number,
        postgis.st_startpoint(geometry) as location,
        alignment_id,
        alignment_version,
        alignment_change_time,
        segment_index as start_segment_index,
        case when segment_index = 0 then null else segment_index - 1 end as end_segment_index,
        segment_count
        from node_segments
        where start_is_node
      union all
      select
        location_track_id,
        location_track_version,
        location_track_name,
        switch_id,
        switch_end_joint_number as joint_number,
        postgis.st_endpoint(geometry) as location,
        alignment_id,
        alignment_version,
        alignment_change_time,
        case when segment_index = segment_count - 1 then null else segment_index + 1 end as start_segment_index,
        segment_index as end_segment_index,
        segment_count
        from node_segments
        where end_is_node
    )
  select
    location_track_id,
    location_track_version,
    location_track_name,
    postgis.st_clusterdbscan(location, 0.5, 1) over () as cluster_id,
    switch_id,
    joint_number,
    location,
    alignment_id,
    alignment_version,
    alignment_change_time,
    start_segment_index,
    end_segment_index,
    segment_count
    from node_points
);

drop index if exists temp_node_point_version_switch_joint_index;
create index temp_node_point_version_switch_joint_index
  on node_point_version (switch_id, joint_number, alignment_change_time);
drop index if exists temp_node_point_version_start_index;
create index temp_node_point_version_start_index
  on node_point_version (alignment_id, alignment_version, start_segment_index);
drop index if exists temp_node_point_version_end_index;
create index temp_node_point_version_end_index
  on node_point_version (alignment_id, alignment_version, end_segment_index);

-- with alignment_version_clusters as (
--   select
--     a.id,
--     a.version,
--     a.change_time,
--     array_agg(np.cluster_id) filter (where np.cluster_id is not null) as cluster_ids,
--     from layout.alignment_version a
--       left join node_point_version np on a.id = np.alignment_id and a.version = np.alignment_version
--     group by a.id, a.version
--     order by a.id, a.version
-- )
-- select
--   id,
--   version,
--   change_time,
--   cluster_ids as new_cluster_ids ,
--   lag(cluster_ids) over (partition by id order by version) as old_cluster_ids
-- from alignment_version_clusters
--  order by id, version;
-- ;
--
-- SELECT ARRAY( SELECT * FROM UNNEST( ARRAY[ 1,2,3 ] ) WHERE UNNEST = ANY( ARRAY[ 2,3 ] ) );
-- SELECT ARRAY[1, 4, 2] && ARRAY[2, 3];
-- select * from node_point_version order by cluster_id, alignment_change_time;
--
-- select
--   cluster_id,
--   array_agg(switch_id) filter (where switch_id is not null) as switch_ids,
--   array_agg(joint_number) filter (where joint_number is not null) as joint_numbers,
--   array_agg(distinct location) locations,
--   array_agg(postgis.st_astext(location)),
--   array_agg(alignment_id) as alignment_ids,
--   array_agg(start_segment_index) as start_segment_indices,
--   array_agg(end_segment_index) as end_segment_indices,
--   count(*) as locations,
--   count(distinct location) as distinct_locations
--   from node_points
--   group by cluster_id;
--
-- select * from node_points where alignment_id = 262 order by coalesce(start_segment_index, end_segment_index);
-- select
--   s.*
--   from layout.alignment a
--     inner join layout.segment_version s on a.id = s.alignment_id and a.version = s.alignment_version
--   where a.id = 262
--   order by s.segment_index;

drop table if exists track_edge_version;
create temporary table track_edge_version as (
  with segment_clusters as (
    select
      lt.id as location_track_id,
      lt.version as location_track_version,
      lt.name as location_track_name,
      s.alignment_id,
      s.alignment_version,
      s.segment_index,
      s.start,
      (
        select array_agg(distinct n_start.cluster_id) filter (where n_start.cluster_id is not null)
      ) as start_cluster_id,
      (
        select array_agg(distinct n_end.cluster_id) filter (where n_end.cluster_id is not null)
      ) as end_cluster_id
      from layout.location_track_version lt
        inner join layout.alignment_version a on a.id = lt.alignment_id and a.version = lt.alignment_version
        inner join layout.segment_version s on a.id = s.alignment_id and a.version = s.alignment_version
        left join node_point_version n_start on a.id = n_start.alignment_id and a.version = n_start.alignment_version and s.segment_index = n_start.start_segment_index
        left join node_point_version n_end on a.id = n_end.alignment_id and a.version = n_end.alignment_version and s.segment_index = n_end.end_segment_index
      group by lt.id, lt.version, s.alignment_id, s.alignment_version, s.segment_index
  )
  select *
    from (
      select
        location_track_id,
        location_track_version,
        location_track_name,
        alignment_id,
        alignment_version,
        segment_index as start_segment_index,
        start as first_segment_start,
        case
          when end_cluster_id is not null then segment_index
          else lead(segment_index) over (partition by location_track_id, location_track_version, alignment_id, alignment_version order by segment_index)
        end as end_segment_index,
        unnest(start_cluster_id) as start_cluster_id,
        unnest(
            coalesce(
                end_cluster_id,
                lead(end_cluster_id) over (partition by location_track_id, location_track_version, alignment_id, alignment_version order by segment_index)
            )
        ) as end_cluster_id
        from segment_clusters
        where start_cluster_id is not null or end_cluster_id is not null
    ) as tmp
    where start_cluster_id is not null
    order by location_track_id, location_track_version, alignment_id, alignment_version, start_segment_index
);
drop index if exists temp_track_edges;
create index temp_track_edges
  on track_edge_version (location_track_id, location_track_version, alignment_id, alignment_version, start_segment_index, end_segment_index);

with alignment_cluster_links as (
  select
    alignment_id,
    alignment_version,
    alignment_change_time,
    array_agg(row (cluster_id, location)) as cluster_link
    from node_point_version
    group by alignment_id, alignment_version, alignment_change_time
    order by alignment_id, alignment_version
)
select
;

insert into layout.node (geometry) (
  select distinct on (cluster_id)
    postgis.st_geomfromtext('MULTIPOINT EMPTY') as geometry
    from node_point_version
);

select * from layout.node;
-- select min(cluster_id), max(cluster_id) from node_points;
-- select min(id), max(id) from layout.node;
-- select min(id), max(id) from layout.node_version;

-- select distinct on (cluster_id) cluster_id as id, row_number() over (order by cluster_id) as row_number from node_points order by cluster_id;
-- alter table node_points add column node_id int null;
drop table if exists cluster_id_to_node_id;
create temporary table cluster_id_to_node_id as (
  with
    cluster as (select distinct cluster_id as id from node_point_version order by id),
    cluster_row as (select id, row_number() over () as row_number from cluster),
    -- Map clusters to nodes: the order doesn't matter, since the nodes are all empty anyhow
    node_row as (select id, row_number() over (order by id) as row_number from layout.node)
    select
      node_row.id node_id,
      cluster_row.id cluster_id
    from cluster_row inner join node_row on cluster_row.row_number = node_row.row_number
);
drop index if exists temp_cluster_id_to_node_id_index;
create index temp_cluster_id_to_node_id_index
  on cluster_id_to_node_id (cluster_id, node_id);


-- select * from node_points;
-- select * from layout.node;

insert into layout.edge (start_node_id, end_node_id, bounding_box, segment_count, length) (
  select
    start_node.node_id as start_node_id,
    end_node.node_id as end_node_id,
    postgis.st_setsrid(postgis.st_extent(geometry), 3067) as bounding_box,
    e.end_segment_index-e.start_segment_index+1 as segment_count,
    sum(postgis.st_m(postgis.st_endpoint(sg.geometry))) as length
--     location_track_id,
--     alignment_id,
--     start_segment_index,
--     end_segment_index,
    from track_edges e
      left join cluster_id_to_node_id start_node on e.start_cluster_id = start_node.cluster_id
      left join cluster_id_to_node_id end_node on e.end_cluster_id = end_node.cluster_id
      left join layout.segment_version s
                on e.alignment_id = s.alignment_id and e.alignment_version = s.alignment_version
                  and s.segment_index between e.start_segment_index and e.end_segment_index+1
      left join layout.segment_geometry sg on sg.id = s.geometry_id
    group by e.alignment_id, e.alignment_version, e.start_segment_index, e.end_segment_index, start_node.node_id, end_node.node_id
) on conflict (start_node_id, end_node_id) do nothing; -- There can be only one

insert into layout.edge_segment_version (
  start_node_id,
  end_node_id,
  edge_version,
  segment_index,
  geometry_alignment_id,
  geometry_element_index,
  start,
  source_start,
  source,
  geometry_id
) (
  select
    start_node.node_id as start_node_id,
    end_node.node_id as end_node_id,
    1 as edge_version,
    s.segment_index - e.start_segment_index as segment_index,
    s.geometry_alignment_id,
    s.geometry_element_index,
    s.start - e.first_segment_start as start,
    s.source_start,
    s.source,
    s.geometry_id
    from track_edges e
      left join cluster_id_to_node_id start_node on e.start_cluster_id = start_node.cluster_id
      left join cluster_id_to_node_id end_node on e.end_cluster_id = end_node.cluster_id
      left join layout.segment_version s
                on e.alignment_id = s.alignment_id and e.alignment_version = s.alignment_version
                  and s.segment_index between e.start_segment_index and e.end_segment_index + 1
      left join layout.segment_geometry sg on sg.id = s.geometry_id
    order by s.alignment_id, s.segment_index
) on conflict (start_node_id, end_node_id, segment_index) do nothing; -- There can be only one

insert into layout.location_track_edge (
  location_track_id,
  location_track_version,
  edge_index,
  start_node_id,
  end_node_id
) (
  select
    e.location_track_id,
    e.location_track_version,
    e.start_segment_index,
    start_node.node_id as start_node_id,
    end_node.node_id as end_node_id
    from track_edges e
      left join cluster_id_to_node_id start_node on e.start_cluster_id = start_node.cluster_id
      left join cluster_id_to_node_id end_node on e.end_cluster_id = end_node.cluster_id
    order by e.location_track_id, e.start_segment_index
);

select switch_id, switch_version, number, postgis.st_astext(location) location, change_time
from layout.switch_joint_version
where switch_id = 1 and number = 1
order by change_time;

select
  lt.name,
  lt.id,
  lt.draft,
  lt.version,
  s.alignment_id, s.alignment_version, s.segment_index,
  s.switch_id, s.switch_start_joint_number, s.switch_end_joint_number,
  postgis.st_astext(postgis.st_startpoint(sg.geometry)) start_point,
  postgis.st_astext(postgis.st_endpoint(sg.geometry)) end_point,
  a.change_time
  from layout.segment_version s
    inner join layout.alignment_version a on s.alignment_id = a.id and s.alignment_version = a.version
    inner join layout.location_track_version lt on lt.alignment_id = a.id and lt.alignment_version = a.version
    inner join layout.segment_geometry sg on s.geometry_id = sg.id
  where s.switch_id = 1 and (s.switch_start_joint_number = 1 or s.switch_end_joint_number = 1)
  order by a.change_time;
-- 2022-11-19 17:23:11.254368 +00:00
select * from node_point_version where switch_id = 1 order by alignment_change_time;
insert into layout.switch_joint_version_2 (
  switch_id,
  switch_version,
  number,
  location,
  location_accuracy,
  node_id
) (
  select
    sj.switch_id,
    sj.switch_version,
    sj.number as number,
    sj.location,
    sj.location_accuracy,
    (
      select node_id
        from node_point_version npv
          inner join cluster_id_to_node_id node on npv.cluster_id = node.cluster_id
        where npv.switch_id = sj.switch_id
          and npv.joint_number = sj.number
          and npv.alignment_change_time <= sj.change_time
        order by npv.alignment_change_time desc
        limit 1
    ) as node_id
    from layout.switch_joint_version sj
)
;

-- insert into layout.switch_joint_version_2 (
--   switch_id,
--   switch_version,
--   number,
--   location,
--   location_accuracy,
--   node_id
-- ) (
--   select
--     node_points.switch_id,
--     node_points.switch_version,
--     node_points.joint_number as number,
--     joint.location,
--     joint.location_accuracy,
--     node.node_id
--     from node_points
--       inner join layout.switch_joint joint
--                  on joint.switch_id = node_points.switch_id
--                    and joint.switch_version = node_points.switch_version
--                    and joint.number = node_points.joint_number
--       inner join cluster_id_to_node_id node on node_points.cluster_id = node.cluster_id
-- );
--
-- select * from layout.edge_segment_version;
select
  lt.alignment_id,
  lt.alignment_version,
  lt.track_number_id,
  lt.external_id,
  lt.name,
  lt.description_base,
  lt.description_suffix,
  lt.type,
  lt.state,
  lt.draft,
  lt.design_id,
  lt.official_row_id,
  lt.design_row_id,
  lt.duplicate_of_location_track_id,
  lt.topological_connectivity,
  lt.owner_id

  from track_edges e
    inner join layout.location_track lt on e.location_track_id = lt.id
