-- Remove old alignment (now in edges) & topology switches (now in nodes) from location tracks
alter table layout.location_track_version
  drop column alignment_id,
  drop column alignment_version,
  drop column topology_start_switch_id,
  drop column topology_start_switch_joint_number,
  drop column topology_end_switch_id,
  drop column topology_end_switch_joint_number;
alter table layout.location_track
  drop column alignment_id,
  drop column alignment_version,
  drop column topology_start_switch_id,
  drop column topology_start_switch_joint_number,
  drop column topology_end_switch_id,
  drop column topology_end_switch_joint_number;

-- Remove switches from segments (now in nodes)
alter table layout.segment_version
  drop column switch_id,
  drop column switch_start_joint_number,
  drop column switch_end_joint_number;

-- Remove location track alignments (= alignments not connected to a reference line)
delete
  from layout.segment_version s
  where not exists(
    select 1
      from layout.reference_line_version rl
      where s.alignment_id = rl.alignment_id and s.alignment_version = rl.alignment_version
  );
delete
  from layout.alignment_version a
  where not exists(
    select 1
      from layout.reference_line_version rl
      where a.id = rl.alignment_id and a.version = rl.alignment_version
  );
delete
  from layout.alignment a
  where not exists(
    select 1
      from layout.reference_line rl
      where a.id = rl.alignment_id and a.version = rl.alignment_version
  );
