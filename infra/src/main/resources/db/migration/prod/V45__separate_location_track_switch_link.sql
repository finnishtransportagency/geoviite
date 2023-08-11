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

-- drop table layout.alignment_switch_joint_version;
-- drop table layout.location_track_switch_joint;
-- drop table layout.location_track_switch_joint_version;
create table layout.alignment_switch_joint_version
(
  alignment_id int not null,
  alignment_version int not null,
  switch_id int not null,
  joint_number int not null,
  joint_m_value decimal(13,6) not null,

  primary key (alignment_id, alignment_version, switch_id, joint_number),

  -- Since this is a version table, it can only reference alignment via its version table
  constraint alignment_switch_joint_version_alignment_fkey
    foreign key (alignment_id, alignment_version)
      references layout.alignment_version(id, version)
  -- Can't reference switch/joint from version table: references layout.switch(id) not null,
);

--  set session geoviite.edit_user to 'INIT';
-- select common.add_metadata_columns('layout', 'location_track_switch_joint');
-- select common.add_metadata_columns('layout', 'alignment_switch_joint_version');

-- Use manual versioning via the parent concept
-- select common.add_table_versioning('layout', 'location_track_switch_joint');

with joint as (
    select alignment_id, alignment_version, switch_id, switch_start_joint_number as number, round(start, 4) as m_value, 's_start' as source, true as pure
      from layout.segment_version sv
      where switch_id is not null and switch_start_joint_number is not null

    union

    select alignment_id, alignment_version, switch_id, switch_end_joint_number as number, round(start + length, 4) as m_value, 's_end' as source, true as pure
      from layout.segment_version sv
      where switch_id is not null and switch_end_joint_number is not null

      union

    select ltv.alignment_id, ltv.alignment_version, ltv.topology_start_switch_id, ltv.topology_end_switch_joint_number, 0.0000 as m_value, 't_start' as source, case when prev_ltv.id is null then true else false end as pure
      from layout.location_track_version ltv
        left join layout.location_track_version prev_ltv on prev_ltv.alignment_id = ltv.alignment_id and prev_ltv.alignment_version = ltv.alignment_version and prev_ltv.version = ltv.version-1
      where ltv.topology_start_switch_id is not null
      group by (ltv.alignment_id, ltv.alignment_version, ltv.topology_start_switch_id, ltv.topology_end_switch_joint_number, prev_ltv.id)

    union

    select ltv.alignment_id, ltv.alignment_version, ltv.topology_end_switch_id, ltv.topology_end_switch_joint_number, av.length, 't_end' as source, case when prev_ltv.id is null then true else false end as pure
      from layout.location_track_version ltv
        left join layout.alignment_version av on av.id = ltv.alignment_id and av.version = ltv.alignment_version
        left join layout.location_track_version prev_ltv on prev_ltv.alignment_id = av.id and prev_ltv.alignment_version = av.version and prev_ltv.version = ltv.version-1
      where ltv.topology_end_switch_id is not null
      group by (ltv.alignment_id, ltv.alignment_version, ltv.topology_end_switch_id, ltv.topology_end_switch_joint_number, av.length, prev_ltv.id)
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
-- insert into layout.alignment_switch_joint_version
select
  *
  from (
    select
      alignment.id as alignment_id,
      alignment.version as alignment_version,
      bool_or(distinct joint.pure) pure,
      joint.switch_id,
      joint.number,
--   joint.m_value,
      s.external_id as s_oid,
      ltv.external_id lt_oid,
--     array_agg(number),
      common.first(joint.m_value) as m_value,
      array_agg(distinct joint.m_value) as m_values,
      array_agg(distinct joint.source)
--   6147, 1, 6020, 5
      from layout.alignment_version alignment
        left join layout.location_track_version ltv
                  on ltv.alignment_id = alignment.id and ltv.alignment_version = alignment.version
        inner join joint on joint.alignment_id = alignment.id and joint.alignment_version = alignment.version
        left join layout.switch s on s.id = joint.switch_id
--       where alignment.id = 6147 and s.id = 6020
      group by alignment.id, alignment.version, joint.switch_id, joint.number, s.external_id, ltv.external_id
  ) as asdf
--   where array_length(asdf.m_values, 1) > 1
    where not(pure)
-- order by alignment_id, alignment_version, switch_id, number
-- select
--   track_v.id as location_track_id,
--   track_v.version as location_track_version,
--   joint.switch_id,
--   number,
--   joint.m_value
--
--   from layout.location_track_version track_v
--     inner join segment_joints joint on joint.alignment_id = track_v.alignment_id and joint.alignment_version = track_v.alignment_version

--   from layout.alignment_version track_v
--     inner join segment_joints joint on joint.alignment_id = track_v.id and joint.alignment_version = track_v.version
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

-- track_version joint links total: ~85k
-- alignment_version joint links total: ~58k
-- segment total count ~460k
select count(*) from layout.segment_version;

select
  ltv.id lt_id,
  ltv.version lt_version,
  ltv.name lt_name,
  ltv.draft lt_draft,
  ltv.change_time lt_change,
  ltv.change_user lt_user,
  av.id a_id,
  av.version a_version,
  av.length a_length,
  av.change_time a_change,
  ssv.id start_s_id,
  ltv.topology_start_switch_joint_number start_s_joint,
  ssv.version start_s_version,
  ssv.name start_s_name,
  ssv.draft start_s_draft,
  ssv.change_time start_s_change,
  ssv.change_user start_s_user,
  esv.id end_s_id,
  ltv.topology_end_switch_joint_number end_s_joint,
  esv.version end_s_version,
  esv.name end_s_name,
  esv.draft end_s_draft,
  esv.change_time end_s_change,
  esv.change_user end_s_user
from layout.location_track_version ltv
--   left join layout.location_track_version
  left join layout.alignment_version av on ltv.alignment_id = av.id and ltv.alignment_version = av.version
  left join layout.switch_at(ltv.change_time) ssv on ssv.id = ltv.topology_start_switch_id
  left join layout.switch_at(ltv.change_time) esv on esv.id = ltv.topology_end_switch_id
where av.id=5677
order by ltv.version;

select
  av.id, av.version, av.change_time,
  postgis.st_astext(
      case
        when sv.switch_start_joint_number = 1 then postgis.st_startpoint(sg.geometry)
        when sv.switch_end_joint_number = 1 then postgis.st_endpoint(sg.geometry)
      end
    ) as location
from layout.segment_version sv
  left join layout.alignment_version av on av.id = sv.alignment_id and av.version = sv.alignment_version
  left join layout.segment_geometry sg on sv.geometry_id = sg.id
where sv.switch_id = 5850;
