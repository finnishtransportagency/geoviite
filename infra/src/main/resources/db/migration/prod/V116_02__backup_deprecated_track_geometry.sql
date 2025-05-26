insert into deprecated.alignment_version
  (id, version, bounding_box, segment_count, length, change_user, change_time)
select
  a.id,
  a.version,
  a.bounding_box,
  a.segment_count,
  a.length,
  a.change_user,
  a.change_time
from layout.alignment_version a
where exists(select 1 from layout.location_track_version where alignment_id = a.id and alignment_version = a.version);

insert into deprecated.alignment_version_segment
  (alignment_id, alignment_version, segment_index, geometry_alignment_id, geometry_element_index, switch_id, switch_start_joint_number, switch_end_joint_number, start_m, source_start_m, source, geometry_id)
select
  a.id,
  a.version,
  s.segment_index,
  s.geometry_alignment_id,
  s.geometry_element_index,
  s.switch_id,
  s.switch_start_joint_number,
  s.switch_end_joint_number,
  s.start,
  s.source_start,
  s.source,
  s.geometry_id
from layout.segment_version s
where exists(select 1 from deprecated.alignment_version a where s.alignment_id = a.id and s.alignment_version = a.version);

insert into deprecated.location_track_version_alignment
  (location_track_id, location_track_layout_context_id, location_track_version, alignment_id, alignment_version, change_user, change_time, deleted)
select
  ltv.id,
  ltv.layout_context_id,
  ltv.version,
  ltv.alignment_id,
  ltv.alignment_version,
  ltv.change_user,
  ltv.change_time,
  ltv.deleted
from layout.location_track_version ltv;
