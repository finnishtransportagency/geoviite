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

-- RR switches came in the init data with all joints in the same location, except for a few switches that didn't have all the joints.
-- This adds the missing joints into the center location for that init version so that they're all alike.
with
  missing_rr_switch_joints as (
    select
      switch.id,
      switch.layout_context_id,
      switch.version,
      structure_joint.number,
      center_joint.location as center_location
      from layout.switch_version switch
        inner join common.switch_structure structure on structure.id = switch.switch_structure_id
        left join common.switch_structure_version_joint structure_joint
                  on structure.id = structure_joint.switch_structure_id
                    and structure.version = structure_joint.switch_structure_version
        left join layout.switch_version_joint switch_joint
                  on switch_joint.switch_id = switch.id
                    and switch_joint.switch_layout_context_id = switch.layout_context_id
                    and switch_joint.switch_version = switch.version
                    and switch_joint.number = structure_joint.number
        left join layout.switch_version_joint center_joint
                  on center_joint.switch_id = switch.id
                    and center_joint.switch_layout_context_id = switch.layout_context_id
                    and center_joint.switch_version = switch.version
                    and center_joint.number = 5
      where switch.version = 1
        and switch.change_user = 'CSV_IMPORT'
        and structure.type like '%RR%'
        and switch_joint.number is null -- is a missing joint
        and center_joint.number is not null -- has a center joint for picking the location
  )
insert into layout.switch_version_joint (switch_id, switch_layout_context_id, switch_version, number, location)
select id, layout_context_id, version, number, center_location
       from missing_rr_switch_joints;


-- -- These are not referenced in the init-data, but the missing joints are relevant in later versions
-- -- where Geoviite did not verify the existence of the joints sufficiently. In each case, only the init version is
-- -- actually missing the joint.
-- insert into layout.switch_version_joint(switch_id, switch_layout_context_id, switch_version, number, location)
--   values
-- -- Switch 19 (missing joint 2) has not moved at all and -> pick the location from the first version that has it
--     (19, 'main_official', 1, 2, postgis.st_point(569642.4889834807,7173613.320921371, 3067)),
-- -- Switch 5588 has not significantly moved since init (<1m) -> pick missing joint locations from the first version that has them
--     (5588, 'main_official', 1, 2, postgis.st_point(428053.3141959699,7208463.118335502, 3067)),
--     (5588, 'main_official', 1, 3, postgis.st_point(428058.7865148145,7208454.438310648, 3067)),
--     (5588, 'main_official', 1, 4, postgis.st_point(428051.6139379415,7208462.43349126, 3067)),
--     (5588, 'main_official', 1, 5, postgis.st_point(428063.60367248335,7208437.572723881, 3067)),
--     (5588, 'main_official', 1, 6, postgis.st_point(428059.86747912335,7208446.848545095, 3067))
-- ;

-- with
--   switch_joint_compare as (
--     select
--       init_s.id,
--       init_s.layout_context_id,
--       init_s.version,
--       init_s.state_category,
--       structure_joint.number,
--       (init_j.number is not null) as init_exists,
--       (next_j.number is not null) as next_exists,
--       postgis.st_distance(init_j.location, next_j.location) as delta,
--       next_j.location as next_location
--       from layout.switch_version init_s
--         inner join common.switch_structure structure
--                    on structure.id = init_s.switch_structure_id
--         left join common.switch_structure_version_joint structure_joint
--                   on structure_joint.switch_structure_id = structure.id
--                     and structure_joint.switch_structure_version = structure.version
--         left join layout.switch_version_joint init_j
--                   on init_j.switch_id = init_s.id
--                     and init_j.switch_layout_context_id = init_s.layout_context_id
--                     and init_j.switch_version = init_s.version
--                     and init_j.number = structure_joint.number
--         inner join layout.switch_version next_s
--                    on next_s.id = init_s.id
--                      and next_s.layout_context_id = init_s.layout_context_id
--                      and next_s.version = init_s.version + 1
--         left join layout.switch_version_joint next_j
--                   on next_j.switch_id = next_s.id
--                     and next_j.switch_layout_context_id = next_s.layout_context_id
--                     and next_j.switch_version = next_s.version
--                     and next_j.number = structure_joint.number
--       where init_s.change_user = 'CSV_IMPORT'
--         and init_s.version = 1
--         and init_s.switch_structure_id = next_s.switch_structure_id
--   ),
--   switch_status as (
--     select
--       id,
--       layout_context_id,
--       version,
--       state_category,
--       min(delta) as delta,
--       array_agg(number) filter (where not init_exists and next_exists) as fixable_joints,
--       array_agg(next_location) filter (where not init_exists and next_exists) as new_locations
--       from switch_joint_compare
--       group by id, layout_context_id, version, state_category
--   )
-- select
--   id,
--   layout_context_id,
--   version,
--   state_category,
--   delta,
--   unnest(fixable_joints) as number,
--   unnest(new_locations) as new_location
-- --   unnest(switch_status.fixable_joints)
--   from switch_status
--   where delta < 1.0
--     and fixable_joints is not null
--
;
