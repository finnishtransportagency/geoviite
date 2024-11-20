-- do $$
--   begin
--       raise exception 'This migration is not yet implemented';
--   end $$;

drop table if exists node_point_version_temp;
create temporary table node_point_version_temp as (
  with
    -- All alignment segments that have links to switches. These are versioned with alignments and produce nodes at the segment ends
    node_switch_segments as (
      select
        lt.id as location_track_id,
        lt.version as location_track_version,
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
        switch_id,
        switch_start_joint_number as joint_number,
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
        location_track_version,
        alignment_id,
        alignment_version,
        switch_id,
        switch_end_joint_number as joint_number,
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
        lt.version as location_track_version,
        lt.alignment_id,
        lt.alignment_version,
        lt.topology_start_switch_id as switch_id,
        lt.topology_start_switch_joint_number as joint_number,
        0 as start_segment_index,
        null as end_segment_index,
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
        lt.topology_end_switch_id as switch_id,
        lt.topology_end_switch_joint_number as joint_number,
        null as start_segment_index,
        a.segment_count - 1 as end_segment_index,
        null::int as start_track_link,
        lt.official_id end_track_link
        from layout.location_track_version lt
          inner join layout.alignment_version a on a.id = lt.alignment_id and a.version = lt.alignment_version
          inner join layout.segment_version s on s.alignment_id = a.id and s.alignment_version = a.version and
                                                 s.segment_index = a.segment_count - 1
          inner join layout.segment_geometry sg on sg.id = s.geometry_id

    ),
    track_node_version_candidate as (
      select
        location_track_id,
        location_track_version,
        alignment_id,
        alignment_version,
        -- Node ordering within the alignment version
        row_number() over (partition by location_track_id, location_track_version, alignment_id, alignment_version order by start_segment_index, end_segment_index) as node_index,
        -- The grouping contracts the duplicated locations to a single node which may have multiple switch links
        -- Duplicate the data into separate arrays for easy unnesting in later processing
        array_agg(switch_id order by switch_id, joint_number) filter (where switch_id is not null) switch_ids,
        array_agg(joint_number order by switch_id, joint_number) filter (where switch_id is not null) switch_joints,
        -- There can only be one or null of these per node
        min(start_track_link) as start_track_link,
        min(end_track_link) as end_track_link,
        -- The locations should all be approximately the same, but there might be minor variation due to floating point errors
        start_segment_index,
        end_segment_index
        from node_points
        group by location_track_id, location_track_version, alignment_id, alignment_version, start_segment_index, end_segment_index
    )
    select
      location_track_id,
      location_track_version,
      alignment_id,
      alignment_version,
      node_index,
      switch_ids,
      switch_joints,
      case when switch_ids is null then start_track_link end as start_track_link,
      case when switch_ids is null then end_track_link end as end_track_link,
      start_segment_index,
      end_segment_index,
      layout.calculate_node_key(switch_ids, switch_joints, start_track_link, end_track_link) as node_key
    from track_node_version_candidate
);

-- select * from node_point_version_temp where start_track_link=1790;

-- Create immutable nodes
-- insert into layout.node(key) select distinct node_key from node_point_version_temp;
insert into layout.node (key, starting_location_track_id, ending_location_track_id)
select distinct on (node_key)
  node_key as key,
  start_track_link,
  end_track_link
  from node_point_version_temp;
insert into layout.node_switch_joint (node_id, switch_id, switch_joint)
select distinct
  id as node_id,
  unnest(switch_ids) as switch_switch_id,
  unnest(switch_joints) as switch_joint
  from (
    select distinct on (np.node_key)
      node.id,
      np.switch_ids,
      np.switch_joints
      from node_point_version_temp np
        inner join layout.node on np.node_key = node.key
      where np.switch_ids is not null
  ) tmp;
-- insert into layout.node_track_end (node_id, starting_location_track_id, ending_location_track_id)
-- select distinct on (np.node_key)
--   node.id as node_id,
--   np.start_track_link,
--   np.end_track_link
--   from node_point_version_temp np
--     inner join layout.node on np.node_key = node.key
--   where np.switch_links is null;

drop table if exists track_edge_version_temp;
create temp table track_edge_version_temp as (
  with edge_version_candidates as (
    select
      node.location_track_id,
      node.location_track_version,
      node.alignment_id,
      node.alignment_version,
      node.node_index as start_node_index,
      lead(node.node_index) over (partition by node.location_track_id, node.location_track_version, node.alignment_id, node.alignment_version order by node.node_index) as end_node_index,
      node.node_key as start_node_key,
      lead(node.node_key) over (partition by node.location_track_id, node.location_track_version, node.alignment_id, node.alignment_version order by node.node_index) as end_node_key,
      node.start_segment_index,
      lead(node.end_segment_index) over (partition by node.location_track_id, node.location_track_version, node.alignment_id, node.alignment_version order by node.node_index) as end_segment_index,
      ltv.change_time,
      ltv.change_user,
      ltv.deleted
      from node_point_version_temp node
        left join layout.location_track_version ltv on ltv.id = node.location_track_id and ltv.version = node.location_track_version
  )
  select
    e.*,
    start_node.id start_node_id,
    end_node.id end_node_id
    from edge_version_candidates e
      inner join layout.node start_node on e.start_node_key = start_node.key
      inner join layout.node end_node on e.end_node_key = end_node.key
);

alter table track_edge_version_temp
  add primary key (location_track_id, location_track_version, start_node_index);

-- select * from track_edge_version_temp;

drop table if exists location_track_edge_version_temp;
create temporary table location_track_edge_version_temp as (
  with
    edge_version_candidates as (
      select
        e.location_track_id,
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
            row (s.geometry_alignment_id, s.geometry_element_index, s.start, s.source_start, s.source, s.geometry_id)
            order by s.segment_index
        ) as segment_data
        from track_edge_version_temp e
          left join layout.segment_version s
                    on s.alignment_id = e.alignment_id
                      and s.alignment_version = e.alignment_version
                      and s.segment_index between e.start_segment_index and e.end_segment_index
        group by e.location_track_id, e.location_track_version, e.start_node_index
    ),
    -- Generate a grouping number that will allow us to contract identical consecutive versions into one
    edge_version_candidates_grouped as (
      select
        *,
        row_number() over (partition by alignment_id, start_node_id, end_node_id order by change_time, alignment_version, location_track_id, location_track_version) as full_version,
        row_number() over (partition by alignment_id, start_node_id, end_node_id, deleted, segment_data order by change_time, alignment_version, location_track_id, location_track_version) as group_version
        from edge_version_candidates e
    ),
    -- Contract the identical consecutive versions into one, leaving a new versioning for the edges
    location_track_edge_versions as (
      select
        alignment_id,
        -- These form the location-track -> edge linkings. Each array must have the same count of elements for unnest
        -- Notably, the edge index for a particular linking may differ even when multiple location track versions share an edge
        array_agg(location_track_id order by location_track_id, location_track_version) as location_track_ids,
        array_agg(location_track_version order by location_track_id, location_track_version) as location_track_versions,
        array_agg(edge_index order by location_track_id, location_track_version) as edge_indexes,
        -- The segment contents can be identical on multiple alignment versions. It doesn't matter which one we pick the data from, so long as it's the same one
        -- The version & change time could be picked by min, but we use the same syntax as others for consistency
        (array_agg(alignment_version order by alignment_version))[1] as alignment_version,
        (array_agg(start_segment_index order by alignment_version))[1] as start_segment_index,
        (array_agg(end_segment_index order by alignment_version))[1] as end_segment_index,
        (array_agg(change_time order by alignment_version))[1] as change_time,
        (array_agg(change_user order by alignment_version))[1] as change_user,
        min(full_version) as full_version,
        start_node_id,
        end_node_id,
        deleted
        from edge_version_candidates_grouped
        group by (full_version - group_version), alignment_id, start_node_id, end_node_id, deleted, segment_data
    ),
    -- Generate ids for the edges, over the above versions
    location_track_edge_ids as (
      select
        alignment_id,
        start_node_id,
        end_node_id,
        row_number() over (order by min(change_time), alignment_id, start_node_id, end_node_id) as id
        from location_track_edge_versions
        group by alignment_id, start_node_id, end_node_id
    )
  select
    v.location_track_ids,
    v.location_track_versions,
    v.alignment_id,
    v.alignment_version,
    v.edge_indexes,
    v.start_segment_index,
    v.end_segment_index,
    id.id as id,
    row_number() over (partition by v.alignment_id, v.start_node_id, v.end_node_id order by full_version) as version,
    v.start_node_id,
    v.end_node_id,
    v.change_time,
    v.change_user,
    v.deleted
    from location_track_edge_versions v
      inner join location_track_edge_ids id on v.alignment_id = id.alignment_id and v.start_node_id = id.start_node_id and v.end_node_id = id.end_node_id
    order by id, version
);

alter table location_track_edge_version_temp add primary key (id, version);

-- select (array_agg(distinct id))[1] from layout.location_track;
-- select * from location_track_edge_version_temp
-- --  where id=91185
-- where id=30765
--   order by id,version
--  ;

insert into layout.location_track_edge_version(
  id,
  version,
  start_node_id,
  end_node_id,
  bounding_box,
  segment_count,
  length,
  change_user,
  change_time,
  deleted
)
select
  ev.id,
  ev.version,
  ev.start_node_id,
  ev.end_node_id,
  postgis.st_extent(sg.geometry) as bounding_box,
  ev.end_segment_index - ev.start_segment_index + 1 as segment_count,
  sum(postgis.st_m(postgis.st_endpoint(sg.geometry))) as length,
  ev.change_user,
  ev.change_time,
  ev.deleted
  from location_track_edge_version_temp ev
    left join layout.segment_version sv
              on sv.alignment_id = ev.alignment_id
                and sv.alignment_version = ev.alignment_version
                and sv.segment_index between ev.start_segment_index and ev.end_segment_index
    left join layout.segment_geometry sg on sg.id = sv.geometry_id
  group by ev.id, ev.version
  ;

alter table layout.location_track_edge
  disable trigger version_update_trigger,
  disable trigger version_row_trigger;
insert into layout.location_track_edge(
  id,
  start_node_id,
  end_node_id,
  bounding_box,
  segment_count,
  length,
  version,
  change_user,
  change_time
)
  overriding system value
with latest as (
  select distinct on (id)
    *
    from layout.location_track_edge_version
    order by id, version
)
select
  id,
  start_node_id,
  end_node_id,
  bounding_box,
  segment_count,
  length,
  version,
  change_user,
  change_time
  from latest
  where deleted = false;
;
alter table layout.location_track_edge
  enable trigger version_update_trigger,
  enable trigger version_row_trigger;
-- select * from layout.location_track_edge_id_seq;
select setval(pg_get_serial_sequence('layout.location_track_edge', 'id'), (select max(id) from layout.location_track_edge));

insert into layout.location_track_edge_segment_version
  (
    edge_id,
    edge_version,
    segment_index,
    geometry_alignment_id,
    geometry_element_index,
    start,
    source_start,
    source,
    geometry_id
  )
select
  ev.id,
  ev.version,
  sv.segment_index - ev.start_segment_index as segment_index,
  sv.geometry_alignment_id,
  sv.geometry_element_index,
  sv.start - first_sv.start,
  sv.source_start,
  sv.source,
  sv.geometry_id
  from location_track_edge_version_temp ev
    left join layout.segment_version first_sv
              on first_sv.alignment_id = ev.alignment_id
                and first_sv.alignment_version = ev.alignment_version
                and first_sv.segment_index = ev.start_segment_index
    left join layout.segment_version sv
              on sv.alignment_id = ev.alignment_id
                and sv.alignment_version = ev.alignment_version
                and sv.segment_index between ev.start_segment_index and ev.end_segment_index
;

insert into layout.location_track_edge_ref_version
  (
    location_track_id,
    location_track_version,
    edge_index,
    edge_id,
    edge_version
  )
select
  unnest(ev.location_track_ids) as location_track_id,
  unnest(ev.location_track_versions) as location_track_version,
  unnest(ev.edge_indexes) as edge_index,
  ev.id as edge_id,
  ev.version as edge_version
  from location_track_edge_version_temp ev
;
