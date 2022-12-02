drop index if exists layout.layout_switch_joint_version_switch_version_index;
create index layout_switch_joint_version_switch_version_index
  on layout.switch_joint_version(switch_id, switch_version, deleted);
