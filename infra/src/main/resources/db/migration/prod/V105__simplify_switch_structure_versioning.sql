create table common.switch_structure_version_joint
(
  switch_structure_id      int                     not null,
  switch_structure_version int                     not null,
  number                   int                     not null,
  location                 postgis.geometry(point) not null,
  primary key (switch_structure_id, switch_structure_version, number),
  foreign key (switch_structure_id, switch_structure_version) references common.switch_structure_version (id, version)
);
comment on table common.switch_structure_version_joint
  is 'Switch structure joint, versioned with parent structure';

create table common.switch_structure_version_alignment
(
  switch_structure_id      int   not null,
  switch_structure_version int   not null,
  alignment_index          int   not null,
  joint_numbers            int[] not null,
  primary key (switch_structure_id, switch_structure_version, alignment_index),
  foreign key (switch_structure_id, switch_structure_version) references common.switch_structure_version (id, version)
);
comment on table common.switch_structure_version_alignment
  is 'Switch structure alignment, versioned with parent structure';

create table common.switch_structure_version_element
(
  switch_structure_id      int                        not null,
  switch_structure_version int                        not null,
  alignment_index          int                        not null,
  element_index            int                        not null,
  type                     common.switch_element_type not null,
  start_point              postgis.geometry(point)    not null,
  end_point                postgis.geometry(point)    not null,
  curve_radius             decimal(12, 6)             null,
  primary key (switch_structure_id, switch_structure_version, alignment_index, element_index),
  foreign key (switch_structure_id, switch_structure_version)
    references common.switch_structure_version (id, version),
  foreign key (switch_structure_id, switch_structure_version, alignment_index)
    references common.switch_structure_version_alignment (switch_structure_id, switch_structure_version, alignment_index)
);
comment on table common.switch_structure_version_element
  is 'Switch structure element, versioned with parent structure. Describes a piece of geometry within an alignment.';

select common.create_timed_fetch_function('common', 'switch_structure');
select common.create_timed_fetch_function('common', 'switch_joint');
select common.create_timed_fetch_function('common', 'switch_alignment');
select common.create_timed_fetch_function('common', 'switch_element');

-- Build a new set of main table versions from all the verioned components
-- Use a temp table, as we'll still need the main table to build this
create temporary table new_structure_version_tmp as with
  all_versions as (
    select distinct id, change_time, change_user, deleted
      from common.switch_structure_version
    union all
    select distinct switch_structure_id as id, change_time, change_user, false as deleted
      from common.switch_joint_version
    union all
    select distinct switch_structure_id as id, change_time, change_user, false as deleted
      from common.switch_alignment_version
    union all
    select distinct a.switch_structure_id as id, e.change_time, e.change_user, false as deleted
      from common.switch_element_version e
        inner join common.switch_alignment_version a on e.alignment_id = a.id
  ),
  new_versions as (
    select
      id,
      bool_or(deleted) as deleted,
      row_number() over (partition by id order by change_time) as version,
      count(*) over (partition by id) as total_versions,
      change_time,
      change_user
      from all_versions
      group by id, change_time, change_user
  )
select
  nv.id,
  ov.type,
  ov.presentation_joint_number,
  nv.version,
  nv.change_user,
  nv.change_time,
  nv.deleted,
  case when nv.total_versions = nv.version then true else false end as is_last_version
  from new_versions nv
    left join common.switch_structure_at(nv.change_time) ov on nv.id = ov.id
  order by id, version;

-- Disable triggers for editing the actual data
alter table common.switch_structure
  disable trigger version_row_trigger,
  disable trigger version_update_trigger;

-- Replace version table contents with the new set. Use upsert instead of truncate-insert to retain foreign key refs
insert into common.switch_structure_version
  (id, type, presentation_joint_number, version, change_user, change_time, deleted)
select id, type, presentation_joint_number, version, change_user, change_time, deleted
  from new_structure_version_tmp
on conflict (id, version) do update set
  type = excluded.type,
  presentation_joint_number = excluded.presentation_joint_number,
  change_user = excluded.change_user,
  change_time = excluded.change_time,
  deleted = excluded.deleted;

-- In the main table, delete the rows whose last version is deleted...
delete from common.switch_structure
       where id in (
         select tmp.id
           from new_structure_version_tmp tmp
           where tmp.deleted = true
             and tmp.is_last_version = true
       );

-- ... and update the rest by the last version
update common.switch_structure
set
  type = sv.type,
  presentation_joint_number = sv.presentation_joint_number,
  version = sv.version,
  change_time = sv.change_time,
  change_user = sv.change_user
  from new_structure_version_tmp sv
  where switch_structure.id = sv.id
    and sv.deleted = false
    and sv.is_last_version = true;

-- Re-enable triggers
alter table common.switch_structure
  enable trigger version_row_trigger,
  enable trigger version_update_trigger;

-- Insert new switch joint versions by structure versions
insert into common.switch_structure_version_joint
  (switch_structure_id, switch_structure_version, number, location)
select
  structure.id as switch_structure_id,
  structure.version as switch_structure_version,
  joint.number,
  joint.location
  from common.switch_structure_version structure
    left join common.switch_joint_at(structure.change_time) joint on joint.switch_structure_id = structure.id;

-- Create new alignment versions into temp table to maintain old id-links
create temporary table alignment_tmp as
select
  structure.id as switch_structure_id,
  structure.version as switch_structure_version,
  alignment.id as alignment_id,
      row_number() over (partition by structure.id, structure.version order by alignment.id) as alignment_index,
  alignment.joint_numbers
  from common.switch_structure_version structure
    left join common.switch_alignment_at(structure.change_time) alignment
              on alignment.switch_structure_id = structure.id;

-- Insert new alignment versions by structure versions
insert into common.switch_structure_version_alignment
  (switch_structure_id, switch_structure_version, alignment_index, joint_numbers)
select
  switch_structure_id,
  switch_structure_version,
  alignment_index,
  joint_numbers
  from alignment_tmp;

-- Insert new element versions by structure versions
insert into common.switch_structure_version_element
  (switch_structure_id, switch_structure_version, alignment_index, element_index, type, start_point, end_point,
   curve_radius)
select
  structure.id as switch_structure_id,
  structure.version as switch_structure_version,
  alignment.alignment_index,
      row_number()
      over (partition by structure.id, structure.version, element.alignment_id order by element.element_index) as element_index,
  element.type,
  element.start_point,
  element.end_point,
  element.curve_radius
  from common.switch_structure_version structure
    left join alignment_tmp alignment
              on structure.id = alignment.switch_structure_id and structure.version = alignment.switch_structure_version
    left join common.switch_element_at(structure.change_time) element on element.alignment_id = alignment.alignment_id;

-- Remove old tables
drop function common.switch_joint_at(timestamp with time zone);
drop function common.switch_element_at(timestamp with time zone);
drop function common.switch_alignment_at(timestamp with time zone);
drop table common.switch_element_version;
drop table common.switch_element;
drop table common.switch_alignment_version;
drop table common.switch_alignment;
drop table common.switch_joint_version;
drop table common.switch_joint;
