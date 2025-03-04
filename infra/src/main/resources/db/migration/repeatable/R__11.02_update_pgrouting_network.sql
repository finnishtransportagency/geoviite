drop function if exists pgrouting.update_network;
create function pgrouting.update_network()
  returns void
  language plpgsql as
$$
  begin

  drop table if exists main_official_edge;
  create temporary table main_official_edge as
    (
      select
        edge.id,
        edge.length,
        array_agg(distinct lt.id order by lt.id) as tracks,
        edge.start_node_id,
        edge.end_node_id,
        postgis.st_force2d(postgis.st_startpoint(start_g.geometry)) as start_point,
        postgis.st_force2d(postgis.st_endpoint(end_g.geometry)) as end_point
        from layout.edge
          left join layout.location_track_version_edge lte on edge.id = lte.edge_id
          left join layout.location_track lt
                    on lte.location_track_id = lt.id and lte.location_track_layout_context_id = lt.layout_context_id and
                       lte.location_track_version = lt.version
          left join layout.edge_segment start_s on edge.id = start_s.edge_id and start_s.segment_index = 0
          left join layout.segment_geometry start_g on start_s.geometry_id = start_g.id
          left join layout.edge_segment end_s on edge.id = end_s.edge_id and end_s.segment_index = (edge.segment_count-1)
          left join layout.segment_geometry end_g on end_s.geometry_id = end_g.id
        where lt.layout_context_id = 'main_official'
        group by edge.id, start_g.id, end_g.id
    );
  alter table main_official_edge add primary key (id);
  create index temp_edge_start on main_official_edge (start_node_id);
  create index temp_edge_end on main_official_edge (end_node_id);

  drop table if exists main_official_node;
  create temporary table main_official_node as
    (
      select distinct on (node.id)
        node.id,
        coalesce(in_edge.end_point, out_edge.start_point) as location
        from layout.node
          left join main_official_edge in_edge on node.id = in_edge.end_node_id
          left join main_official_edge out_edge on node.id = out_edge.start_node_id
        where in_edge.id is not null or out_edge.id is not null
    );
  alter table main_official_node add primary key (id);

  insert into pgrouting.node(id, location)
  select id, location from main_official_node
  on conflict (id) do update set location = excluded.location;
  delete from pgrouting.node where not exists(select 1 from main_official_node where id = pgrouting.node.id);

  insert into pgrouting.edge(id, length, tracks, start_node_id, end_node_id)
  select id, length, tracks, start_node_id, end_node_id from main_official_edge
  on conflict (id) do update set tracks = excluded.tracks;
  delete from pgrouting.edge where not exists (select 1 from main_official_edge where id = pgrouting.edge.id);

  drop table main_official_edge;
  drop table main_official_node;

  end;
$$;

select pgrouting.update_network();
