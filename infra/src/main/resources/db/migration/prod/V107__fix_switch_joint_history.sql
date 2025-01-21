-- Some initial import switches were missing joints that were referenced from the alignments
-- Add said joints, though only if the structure agrees that they should exist
with
  segments as (
    select
      s.switch_id,
      s.switch_start_joint_number,
      s.switch_end_joint_number,
      g.geometry
      from layout.segment_version s
        inner join layout.alignment_version a on a.id = s.alignment_id and a.version = s.alignment_version
        left join layout.segment_geometry g on g.id = s.geometry_id
      where a.change_user = 'CSV_IMPORT'
  ),
  referenced_joints as (
    select distinct on (id, joint) from (
      select switch_id id, switch_start_joint_number joint, postgis.st_startpoint(geometry) as location from segments
      union all
      select switch_id id, switch_end_joint_number joint, postgis.st_endpoint(geometry) as location from segments
    ) tmp
  ),
  switches as (
    select
      s.id,
      s.layout_context_id,
      s.version,
      s.name,
      ss.type,
      array_agg(distinct sj.number) switch_joints,
      array_agg(distinct ssj.number) structure_joints
      from layout.switch_version s
        inner join common.switch_structure ss on ss.id = s.switch_structure_id
        left join layout.switch_version_joint sj on sj.switch_id = s.id and sj.switch_layout_context_id = s.layout_context_id and sj.switch_version = s.version
        left join common.switch_structure_version_joint ssj on ss.id = ssj.switch_structure_id and ss.version = ssj.switch_structure_version
      where s.layout_context_id = 'main_official' and s.change_user = 'CSV_IMPORT'
      group by s.id, s.layout_context_id, s.version, ss.id
  )
insert into layout.switch_version_joint (
  switch_id,
  switch_layout_context_id,
  switch_version,
  number,
  location,
  location_accuracy
)
select
  s.id,
  s.layout_context_id,
  s.version,
  rj.joint,
  rj.location,
  null
  from referenced_joints rj
    left join switches s on s.id = rj.id
  where (not rj.joint = any (s.switch_joints))
    and (rj.joint = any (s.structure_joints));
;
