drop index if exists layout.layout_switch_joint_location_location_index;
create index layout_switch_joint_location_location_index
  on layout.switch_joint_version using gist (location) include (switch_id);
