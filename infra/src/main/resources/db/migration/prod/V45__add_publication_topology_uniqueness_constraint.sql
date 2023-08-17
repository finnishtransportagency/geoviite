-- Older versions allowed object-by-object revert of changes, which led to a double-linking in topology
-- Fix the data before adding the constraint

update layout.location_track_version
set topology_end_switch_id = null, topology_end_switch_joint_number = null
where topology_end_switch_id = topology_start_switch_id;

-- In joints we delete the end-link by selecting the higher address
delete
  from publication.switch_joint joint
  where exists(
    select 1
      from publication.switch_joint other
      where joint.publication_id = other.publication_id
        and joint.switch_id = other.switch_id
        and joint.joint_number = other.joint_number
        and joint.location_track_id = other.location_track_id
        and joint.address > other.address
  );

-- Add the actual constraint so no such publications can be made in the future
alter table publication.switch_joint
  add constraint publication_switch_joint_location_track_unique
    unique  (publication_id, switch_id, joint_number, location_track_id);
