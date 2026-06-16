-- increment this to force re-execution: 1
drop view if exists layout.track_number_version_ends_view;
create view layout.track_number_version_ends_view as
select
  tnv.id,
  tnv.layout_context_id,
  tnv.version,
  postgis.st_startpoint(first_geom.geometry) as start_point,
  postgis.st_endpoint(last_geom.geometry)    as end_point
  from layout.track_number_version tnv
    left join layout.track_number_version_segment first_seg
              on tnv.id = first_seg.track_number_id
                and tnv.layout_context_id = first_seg.track_layout_context_id
                and tnv.version = first_seg.track_number_version
                and first_seg.segment_index = 0
    left join layout.segment_geometry first_geom on first_seg.geometry_id = first_geom.id
    left join layout.track_number_version_segment last_seg
              on tnv.id = last_seg.track_number_id
                and tnv.layout_context_id = last_seg.track_layout_context_id
                and tnv.version = last_seg.track_number_version
                and last_seg.segment_index = tnv.segment_count - 1
    left join layout.segment_geometry last_geom on last_seg.geometry_id = last_geom.id;
