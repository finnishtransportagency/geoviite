drop function if exists layout.get_or_insert_edge;
create function layout.get_or_insert_edge(
  start_node_id int,
  end_node_id int,
  geometry_alignment_ids int[],
  geometry_element_indices int[],
  start_m_values decimal(13,6)[],
  source_start_m_values decimal(13,6)[],
  sources varchar[],
  geometry_ids int[]
) returns int as
$$
declare
  declare edge_hash uuid;
  declare edge_bbox postgis.box2d;
  declare edge_length decimal(13, 6);
  declare result_id int;
begin
  drop table if exists segment_tmp;
  create temporary table segment_tmp as
    select
      row_number() over (order by tmp.start_m) - 1 as segment_index,
      tmp.*,
      layout.calculate_segment_hash(
          geometry_alignment_id,
          geometry_element_index,
          source_start,
          source,
          geometry_id
      ) as hash,
      sg.bounding_box,
      postgis.st_m(postgis.st_endpoint(sg.geometry)) as length
      from (
        select
          unnest(geometry_alignment_ids) as geometry_alignment_id,
          unnest(geometry_element_indices) as geometry_element_index,
          unnest(start_m_values) as start_m,
          unnest(source_start_m_values) as source_start,
          unnest(sources)::layout.geometry_source as source,
          unnest(geometry_ids) as geometry_id
      ) tmp
        left join layout.segment_geometry sg on tmp.geometry_id = sg.id;

  select into edge_hash, edge_bbox, edge_length
    layout.calculate_edge_hash(start_node_id, end_node_id, array_agg(segment_tmp.hash)),
    postgis.st_extent(segment_tmp.bounding_box),
    sum(segment_tmp.length)
    from segment_tmp;

  -- Try inserting edge: if it already exists, the hash will conflict
  insert into layout.edge(start_node_id, end_node_id, bounding_box, segment_count, length, hash)
    values (
      start_node_id,
      end_node_id,
      edge_bbox,
      array_length(geometry_ids, 1),
      edge_length,
      edge_hash
    )
  on conflict do nothing
    returning id into result_id;

  -- If the row was inserted (no conflict) then the id is not null -> insert the rest of the data
  if result_id is not null then
    insert into layout.edge_segment
      (edge_id, segment_index, geometry_alignment_id, geometry_element_index, start, source_start, source, geometry_id)
    select
      result_id as edge_id,
      segment_tmp.segment_index,
      segment_tmp.geometry_alignment_id,
      segment_tmp.geometry_element_index,
      segment_tmp.start_m,
      segment_tmp.source_start,
      segment_tmp.source,
      segment_tmp.geometry_id
    from segment_tmp;
    drop table segment_tmp;
    return result_id;
  else -- Insert yielded nothing, so the edge already exists
    select id from layout.edge where hash = edge_hash into result_id;
    drop table segment_tmp;
    return result_id;
  end if;
end;
$$ language plpgsql volatile;
