-- switch versions

-- temporary drop to allow version history to be rewritten
alter table publication.switch
  drop constraint publication_switch_switch_version_fk;

alter table layout.switch_version
  drop constraint switch_version_pkey,
  add constraint switch_version_pkey primary key (id, layout_context_id, version);

alter table publication.switch
  add column layout_context_id text;
update publication.switch
set layout_context_id = 'main_official';
alter table publication.switch alter column layout_context_id set not null;

create temporary table switch_version_change on commit drop as
  (
    select
      layout_context_id,
      official_id as switch_official_id,
      id,
      version as old_version,
          row_number() over (partition by layout_context_id, official_id order by change_time, version) as new_version
      from layout.switch_version
  );

create temporary table switch_joint_version_change on commit drop as
  (
    select
      svc.layout_context_id,
      svc.switch_official_id as switch_official_id,
      svc.id as switch_row_id,
      svc.old_version as old_switch_version,
      svc.new_version as new_switch_version,
      number,
      sjv.version as old_joint_version,
          row_number()
          over (partition by switch_official_id, layout_context_id, number order by change_time, sjv.version) as new_joint_version
      from switch_version_change svc
        join layout.switch_joint_version sjv
             on svc.id = sjv.switch_id
               and svc.old_version = sjv.switch_version
  );

update publication.switch
set switch_version = switch_version_change.new_version
  from switch_version_change
  where switch.layout_context_id = switch_version_change.layout_context_id
    and switch.switch_id = switch_version_change.id
    and switch.switch_version = switch_version_change.old_version;

update layout.switch_version
set version = switch_version_change.new_version, id = official_id
  from switch_version_change
  where switch_version.layout_context_id = switch_version_change.layout_context_id
    and switch_version.id = switch_version_change.id
    and switch_version.version = switch_version_change.old_version;

update layout.switch_joint_version
set version = sjvc.new_joint_version, switch_version = sjvc.new_switch_version, switch_id = sjvc.switch_official_id
  from switch_joint_version_change sjvc
  where switch_joint_version.switch_id = sjvc.switch_row_id
    and switch_joint_version.switch_layout_context_id = sjvc.layout_context_id
    and switch_joint_version.switch_version = sjvc.old_switch_version
    and switch_joint_version.version = sjvc.old_joint_version
    and switch_joint_version.number = sjvc.number;

-- Constraint will be recreated in redo_id_structure_for_switch after we recreate switch's pkey.
-- We need to update this table here because it's where we have easy access to the switch official IDs.
alter table layout.switch_joint
  drop constraint switch_joint_switch_id_fkey;

alter table layout.switch_joint
  disable trigger version_update_trigger;
alter table layout.switch_joint
  disable trigger version_row_trigger;

update layout.switch_joint
set switch_id = switch_official_id, switch_version = new_switch_version, version = new_joint_version
  from switch_joint_version_change sjvc
  where switch_joint.switch_id = sjvc.switch_row_id
    and switch_joint.switch_version = sjvc.old_switch_version
    and switch_joint.number = sjvc.number
    and switch_joint.version = sjvc.old_joint_version;

alter table layout.switch_joint
  enable trigger version_update_trigger;
alter table layout.switch_joint
  enable trigger version_row_trigger;

alter table publication.switch
  add constraint publication_switch_switch_version_fk
    foreign key (switch_id, layout_context_id, switch_version)
      references layout.switch_version (id, layout_context_id, version);

alter table layout.switch_version
  add column origin_design_id int;
update layout.switch_version
set origin_design_id = design_row.design_id
  from layout.switch_version design_row
  where design_row.id = switch_version.design_row_id
    and not exists (
    select *
      from layout.switch_version future_design_row
      where future_design_row.id = design_row.id
        and future_design_row.layout_context_id = design_row.layout_context_id
        and future_design_row.version > design_row.version
  );

alter table layout.switch_version
  drop column official_id;
alter table layout.switch_version
  drop column design_row_id;
alter table layout.switch_version
  drop column official_row_id;
