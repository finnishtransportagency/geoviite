drop function if exists layout.get_or_insert_edge;
drop function if exists layout.get_or_insert_edges;
drop type if exists layout.edge_insert_result;

create type layout.edge_insert_result as
(
  insert_edge_id int,
  edge_id        int
);

create function layout.get_or_insert_edges(
  insert_edge_ids int[],
  start_node_ids int[],
  start_node_ports layout.node_port_type[],
  end_node_ids int[],
  end_node_ports layout.node_port_type[],
  geometry_alignment_idss int[],
  geometry_element_indicess int[],
  start_m_valuess decimal(13, 6)[],
  source_start_m_valuess decimal(13, 6)[],
  sourcess layout.geometry_source[],
  geometry_idss int[],
  edge_bboxes postgis.geometry(polygon, 3067)[],
  segment_index_range_starts int[],
  segment_index_range_ends int[])
  returns setof layout.edge_insert_result as
$$
begin
  -- one row per insert_edge_id, with segment info in arrays
  create temporary table edge_content on commit drop as (
    select te.*
      from (
        select
          unnest(segment_index_range_starts) + 1 as segment_ix_start,
          unnest(segment_index_range_ends) + 1 as segment_ix_end,
          generate_series(1, cardinality(insert_edge_ids)) as ix
      ) ixr,
        lateral (
          select
            insert_edge_ids[ix] as insert_edge_id,
            start_node_ids[ix] as start_node_id,
            start_node_ports[ix] as start_node_port,
            end_node_ids[ix] as end_node_id,
            end_node_ports[ix] as end_node_port,
            geometry_alignment_idss[segment_ix_start : segment_ix_end] as geometry_alignment_ids,
            geometry_element_indicess[segment_ix_start : segment_ix_end] as geometry_element_indices,
            start_m_valuess[segment_ix_start : segment_ix_end] as start_m_values,
            source_start_m_valuess[segment_ix_start : segment_ix_end] as source_start_m_values,
            sourcess[segment_ix_start : segment_ix_end] as sources,
            geometry_idss[segment_ix_start : segment_ix_end] as geometry_ids,
            edge_bboxes[ix] as edge_bbox
          ) te
  );
  create index on edge_content (insert_edge_id);

  -- one row per segment, identified by (insert_edge_id, segment_index)
  create temporary table segment on commit drop as (
    select
          row_number()
          over (partition by start_node_id, start_node_port, end_node_id, end_node_port order by start_m) -
          1 as segment_index,
      layout.calculate_segment_hash(
          geometry_alignment_id,
          geometry_element_index,
          source_start_m,
          source,
          geometry_id
      ) as hash,
      segment_content.*,
      postgis.st_m(postgis.st_endpoint(sg.geometry)) as segment_length
      from (
        select
          insert_edge_id,
          start_node_id,
          start_node_port,
          end_node_id,
          end_node_port,
          unnest(start_m_values) as start_m,
          unnest(geometry_alignment_ids) as geometry_alignment_id,
          unnest(geometry_element_indices) as geometry_element_index,
          unnest(source_start_m_values) as source_start_m,
          unnest(sources) as source,
          unnest(geometry_ids) as geometry_id
          from edge_content
      ) segment_content
        left join layout.segment_geometry sg on segment_content.geometry_id = sg.id
  );

  create index on segment(insert_edge_id);

  return query
    -- one row per insert_edge_id
    with edge_hash as (
      select
        insert_edge_id,
        layout.calculate_edge_hash(
            start_node_id,
            start_node_port,
            end_node_id,
            end_node_port,
            array_agg(segment.hash order by segment_index)
        ) as hash,
        sum(segment_length) as edge_length
        from segment
        group by insert_edge_id, start_node_id, start_node_port, end_node_id, end_node_port
    ),
      -- one row per distinct hash
      distinct_edge_hash as (
        select distinct on (hash) *
          from edge_hash
      ),
      inserted_edge as (
        insert into layout.edge
          (start_node_id,
           start_node_port,
           end_node_id,
           end_node_port,
           bounding_box,
           start_location,
           end_location,
           segment_count,
           length,
           hash)
          (
            select
              start_node_id,
              start_node_port,
              end_node_id,
              end_node_port,
              edge_bbox,
              postgis.st_force2d(postgis.st_startpoint(start_segment_geometry.geometry)),
              postgis.st_force2d(postgis.st_endpoint(end_segment_geometry.geometry)),
              array_length(geometry_ids, 1),
              edge_length,
              distinct_edge_hash.hash
              from edge_content
                join distinct_edge_hash using (insert_edge_id)
                join layout.segment_geometry start_segment_geometry
                     on start_segment_geometry.id = geometry_ids[1]
                join layout.segment_geometry end_segment_geometry
                     on end_segment_geometry.id = geometry_ids[array_length(geometry_ids, 1)]
          )
          on conflict do nothing
          returning id, hash
      ),
      inserted_segment as (
        insert into layout.edge_segment
          (edge_id, segment_index, geometry_alignment_id, geometry_element_index, start_m, source_start_m,
           source,
           geometry_id, hash)
          (
            select
              inserted_edge.id as edge_id,
              segment_index,
              geometry_alignment_id,
              geometry_element_index,
              start_m,
              source_start_m,
              source,
              geometry_id,
              segment.hash
              from inserted_edge
                join distinct_edge_hash using (hash)
                join segment using (insert_edge_id)
          )
      )
    select insert_edge_id, id
      from edge_hash
        join (
        select hash, id
          from inserted_edge
        union all
        select hash, id
          from layout.edge
      ) e using (hash);

  drop table edge_content;
  drop table segment;
end;

$$
  language plpgsql
  volatile;
