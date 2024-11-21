-- switch joints

alter table layout.switch_joint
  disable trigger version_update_trigger;
alter table layout.switch_joint
  disable trigger version_row_trigger;

alter table layout.switch_joint add column switch_layout_context_id text;
update layout.switch_joint
set switch_layout_context_id = switch.layout_context_id
  from layout.switch
  where switch_joint.switch_id = switch.id
    and switch_joint.switch_version = switch.version;

alter table layout.switch_joint
  enable trigger version_update_trigger;
alter table layout.switch_joint
  enable trigger version_row_trigger;

alter table layout.switch_joint
  drop constraint switch_joint_pkey,
  add constraint switch_joint_pkey primary key (switch_id, switch_layout_context_id, number);

drop function layout.get_switch_joint_version(int, int);
select common.create_version_fetch_function('layout', 'switch_joint');
