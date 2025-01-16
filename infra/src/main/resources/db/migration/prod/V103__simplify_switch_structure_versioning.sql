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

alter table common.switch_structure
  disable trigger version_row_trigger,
  disable trigger version_update_trigger;

-- These are the only cases of updating switch structures thus far. Here, only the joints were updated.
-- In the new structure-based versioning, we need to have separate parent versions for the change as well.
insert into common.switch_structure_version
  (id, type, presentation_joint_number, version, change_user, change_time, deleted)
select distinct on (structure.id)
  structure.id,
  structure.type,
  structure.presentation_joint_number,
  2 as version,
  joint.change_user as change_user,
  joint.change_time as change_time,
  false as deleted
  from common.switch_structure structure
    inner join common.switch_joint_version joint
               on structure.id = joint.switch_structure_id and joint.version = 2;

-- Update the main table to match the new versions as well
update common.switch_structure
set version = sv.version, change_time = sv.change_time, change_user = sv.change_user
  from common.switch_structure_version sv
  where sv.version = 2 and switch_structure.id = sv.id;

alter table common.switch_structure
  enable trigger version_row_trigger,
  enable trigger version_update_trigger;

-- select common.create_timed_fetch_function('common', 'switch_structure');
select common.create_timed_fetch_function('common', 'switch_joint');
select common.create_timed_fetch_function('common', 'switch_alignment');
select common.create_timed_fetch_function('common', 'switch_element');

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
