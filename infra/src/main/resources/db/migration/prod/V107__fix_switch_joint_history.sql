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
    select distinct on (id, joint) * from (
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

-- These are not referenced in the init-data, but the missing joints are relevant in later versions
-- where Geoviite did not verify the existence of the joints sufficiently. In each case, only the init version is
-- actually missing the joint.
insert into layout.switch_version_joint(switch_id, switch_layout_context_id, switch_version, number, location)
values
-- Switch 19 (missing joint 2) has not moved at all and -> pick the location from the first version that has it
  (19, 'main_official', 1, 2, postgis.st_point(569642.4889834807,7173613.320921371, 3067)),
-- Switch 5588 has not significantly moved since init (<1m) -> pick missing joint locations from the first version that has them
  (5588, 'main_official', 1, 2, postgis.st_point(428053.3141959699,7208463.118335502, 3067)),
  (5588, 'main_official', 1, 3, postgis.st_point(428058.7865148145,7208454.438310648, 3067)),
  (5588, 'main_official', 1, 4, postgis.st_point(428051.6139379415,7208462.43349126, 3067)),
  (5588, 'main_official', 1, 5, postgis.st_point(428063.60367248335,7208437.572723881, 3067)),
  (5588, 'main_official', 1, 6, postgis.st_point(428059.86747912335,7208446.848545095, 3067)),
-- Switch 5510 is RR which initially came with all joints in one position -> pick location for missing joints from the existing one
  (5510, 'main_official', 1, 1, postgis.st_point(483824.65133276046,6747766.200798029, 3067)),
  (5510, 'main_official', 1, 2, postgis.st_point(483824.65133276046,6747766.200798029, 3067)),
  (5510, 'main_official', 1, 3, postgis.st_point(483824.65133276046,6747766.200798029, 3067)),
  (5510, 'main_official', 1, 4, postgis.st_point(483824.65133276046,6747766.200798029, 3067)),
-- OPTIONAL: Switch 5511 is RR which initially came with all joints in one position -> pick location for missing joints from the existing one
  (5511, 'main_official', 1, 1, postgis.st_point(483587.25074875564,6747744.423612414, 3067)),
  (5511, 'main_official', 1, 2, postgis.st_point(483587.25074875564,6747744.423612414, 3067)),
  (5511, 'main_official', 1, 3, postgis.st_point(483587.25074875564,6747744.423612414, 3067)),
  (5511, 'main_official', 1, 4, postgis.st_point(483587.25074875564,6747744.423612414, 3067))
;
