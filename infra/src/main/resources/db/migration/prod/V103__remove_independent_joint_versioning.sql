-- Joints now follow the main switch table versioning, so there is no need for a main joint table
drop table layout.switch_joint;

-- Drop deleted version rows, as their existence is resolved by the switch version existing
delete from layout.switch_joint_version where deleted = true;

alter table layout.switch_joint_version
  -- Drop old primary key
  drop constraint switch_joint_version_pkey,
  -- The joints are always written with the switch itself -> no need for separate metadata
  drop column version,
  drop column deleted,
  drop column change_user,
  drop column change_time,
  -- Add new primary key
  add primary key (switch_id, switch_layout_context_id, switch_version, number);
