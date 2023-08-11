alter table layout.segment_version
  drop constraint segment_version_pkey,
  drop constraint segment_version_alignment_version_fkey;
alter table layout.alignment_version drop constraint alignment_version_pkey;

with alignment_version_pairs as
  (
    select
      id,
      version,
      lag(id) over (partition by id order by version) prev_id,
      lag(version) over (partition by id order by version) prev_version,
      alignment_id,
      alignment_version,
      lag(alignment_id) over (partition by id order by version) prev_alignment_id,
      lag(alignment_version) over (partition by id order by version) prev_alignment_version,
      array[ topology_start_switch_id, topology_start_switch_joint_number, topology_end_switch_id, topology_end_switch_joint_number ] topology,
      lag(array[ topology_start_switch_id, topology_start_switch_joint_number, topology_end_switch_id, topology_end_switch_joint_number ]) over (partition by id order by version) prev_topology
    from layout.location_track_version
  ),
  missing_versions as (
    select
      id,
      version,
      alignment_id,
      alignment_version
      from alignment_version_pairs
      where alignment_id = prev_alignment_id
        and alignment_version = prev_alignment_version
        and topology <> prev_topology
  )
--      ,
--   missing_versions_agg as (
--     select
--       id,
--       array_agg(version) versions,
--       alignment_id,
--       array_agg(alignment_version) prev_alignment_versions
--     from alignment_version_pairs
--     where alignment_id = prev_alignment_id
--       and alignment_version = prev_alignment_version
--       and topology <> prev_topology
--     group by id, alignment_id
--   )
     ,
  versions_to_add as (
    select
      id,
      version,
      alignment_id,
      alignment_version as old_version,
      alignment_version+count(*) over (
        partition by alignment_id order by alignment_version rows between unbounded preceding and 0 preceding
      ) as new_version
    from missing_versions missing
  ),
  versions_to_move as (
    select av.id, av.version, count(*) version_delta
    from layout.alignment_version av
      inner join versions_to_add added on av.id = added.alignment_id and av.version > added.old_version
    group by av.id, av.version
  ),
  moved as (
    update layout.alignment_version av set
      version = av.version + version_delta
    from versions_to_move todo
    where av.id = todo.id
      and av.version = todo.version
    returning
      av.id,
      av.version - todo.version_delta as old_version,
      av.version as new_version,
      'MOVED' as change
  ),
  moved_segments as (
    update layout.segment_version sv set
      alignment_version = moved.new_version
    from moved
    where sv.alignment_id = moved.id
      and sv.alignment_version = moved.old_version
    returning moved.id, moved.old_version, moved.new_version, moved.change, sv.segment_index
  )
--   moved_versions as (
--     update layout.alignment_version av set
--       version = version + count(id = )
--     from missing_versions missing
--     where av.id = missing.id
--       and av.version > missing.version
--   )
--      ,
--   added_versions as (
--     insert into layout.alignment_version
--   )

-- select * from missing_versions order by id --, version
-- select * from
--   (select
--      alignment_id as id,
--      null as old_version,
--      new_version,
--      'ADD' as change
--    from versions_to_add
--     union all
--   select
--     id,
--     version as old_version,
--     version+version_delta as new_version,
--     'MOVE' as change
--    from versions_to_move
--   ) changes
-- order by id, new_version

select
  id, old_version, new_version, array_agg(segment_index) segments, change
from moved_segments
group by id, old_version, new_version, change
;

alter table layout.alignment_version add constraint alignment_version_pkey primary key (id, version);
alter table layout.segment_version
  add constraint segment_version_pkey primary key (alignment_id, alignment_version, segment_index),
  add constraint segment_version_alignment_version_fkey foreign key (alignment_id, alignment_version) references layout.alignment_version;
