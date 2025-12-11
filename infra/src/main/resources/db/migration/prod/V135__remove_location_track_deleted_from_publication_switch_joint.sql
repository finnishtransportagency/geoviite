-- Links from deleted tracks are not considered to be switch track joints any longer.
-- Thus a deleted track means a deleted joint location.
update publication.switch_joint
set removed = true
  where location_track_deleted;

-- Remove the redundant column
alter table publication.switch_joint
  drop column location_track_deleted;
