alter table publication.split_updated_duplicate
  drop constraint split_duplicate_fkey;

alter table publication.split_updated_duplicate
  rename duplicate_id to duplicate_location_track_id;

alter table publication.split_updated_duplicate
  add constraint split_updated_duplicate_location_track_fkey foreign key (duplicate_location_track_id)
    references layout.location_track (id);

alter table publication.split_updated_duplicate_version
  drop constraint split_updated_duplicate_version_pkey;

alter table publication.split_updated_duplicate_version
  rename duplicate_id to duplicate_location_track_id;

alter table publication.split_updated_duplicate_version
  add constraint split_updated_duplicate_version_pkey unique (split_id, duplicate_location_track_id, version);

drop function publication.get_split_updated_duplicate_version;
select common.create_version_fetch_function('publication', 'split_updated_duplicate');
