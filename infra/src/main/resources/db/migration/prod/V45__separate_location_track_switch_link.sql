-- Table-by-link:
-- + Ensures each switch is connected once
-- + Minimalist data amount (likely a non-issue)
-- - Does not ensure that joints are connected one. Could perhaps be done as a separate constraint
-- - Joint data in 2 tables, making it tricky to use in sql
-- + Easy to see: joint sequence, track's switch count (or vice versa)
-- - Hard to see: joint m-value, m-value range for switch-joint-link (-> relevant geometry in alignment)
-- create table layout.location_track_switch
-- (
--   location_track_id int,
--   location_track_version int,
--   switch_id int references layout.switch(id),
--   joint_numbers int[],
--   joint_m_values decimal(13,6)[],
--
--   primary key (location_track_id, switch_id),
--
--   constraint location_track_switch_location_track_fkey
--     foreign key (location_track_id, location_track_version)
--       references layout.location_track(id, version)
-- );

-- Table-by-joint:
-- + Ensures each switch-joint is connected once
-- - Duplicates the ids -> more data. Not enough to be an issue, though.
-- - Does not ensure that the switch isn't connected multiple times, unless they have the same joints
-- + Easy to see: joint m-value, m-value range for switch-joint-link (-> relevant geometry in alignment)
-- - Hard to see (grouping and aggregates): joint sequence, track's switch count (or vice versa)
alter table layout.location_track
  add constraint location_track_id_version_unique unique (id, version);

create table layout.location_track_switch_joint
(
  location_track_id int,
  location_track_version int,
  switch_id int references layout.switch(id),
  joint_number int,
  joint_m_value decimal(13,6),

  primary key (location_track_id, switch_id, joint_number),

  constraint location_track_switch_location_track_fkey
    foreign key (location_track_id, location_track_version)
      references layout.location_track(id, version)
);

-- set session geoviite.edit_user to 'INIT';
select common.add_metadata_columns('layout', 'location_track_switch_joint');
select common.add_table_versioning('layout', 'location_track_switch_joint');

with segment_joints as (
    select alignment_id, alignment_version, switch_id, switch_start_joint_number as number, round(start, 5) as m_value
      from layout.segment_version
      where switch_id is not null and switch_start_joint_number is not null

    union

    select alignment_id, alignment_version, switch_id, switch_end_joint_number as number, round(start + length, 5) as m_value
      from layout.segment_version
      where switch_id is not null and switch_end_joint_number is not null
)

-- select
--   track.id as location_track_id,
--   joints.switch_id,
--   array_agg(number order by m_value) joint_numbers,
--   array_agg(joints.m_value order by m_value) m_values
--
--   from layout.location_track track
--     inner join segment_joints joints
--                on joints.alignment_id = track.alignment_id and joints.alignment_version = track.alignment_version
--
--   group by track.id, joints.switch_id
-- insert into layout.location_track_switch_joint_version
select
  track_v.id as location_track_id,
  track_v.version as location_track_version,
  joint.switch_id,
  number,
  joint.m_value

  from layout.location_track_version track_v
    inner join segment_joints joint on joint.alignment_id = track_v.alignment_id and joint.alignment_version = track_v.alignment_version

--   group by track_v.id, track_v.version, joint.switch_id, joint.number, joint.m_value
;

-- select * from (
--   select
--     track.id as location_track_id,
--     track.name,
--     count(distinct segment.switch_id) switch_count,
--     array_agg(segment.switch_id) switches
--     from layout.location_track track
--       left join layout.alignment_version alignment
--                 on track.alignment_id = alignment.id and alignment.version = track.alignment_version
--       left join layout.segment_version segment
--                 on alignment.id = segment.alignment_id and alignment.version = segment.alignment_version
--     where segment.switch_id is not null
--     group by track.id
-- ) asd order by switch_count desc
