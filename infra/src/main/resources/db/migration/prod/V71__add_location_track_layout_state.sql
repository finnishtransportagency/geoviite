create type layout.location_track_state as enum ('IN_USE', 'NOT_IN_USE', 'PLANNED', 'DELETED', 'BUILT');

alter table layout.location_track disable trigger version_update_trigger;
alter table layout.location_track disable trigger version_row_trigger;

drop view if exists layout.location_track_change_view;
drop view if exists layout.location_track_publication_view;

alter table layout.location_track drop constraint if exists location_track_unique_official_name;

alter table layout.location_track
  alter column state type layout.location_track_state
  using state::text::layout.location_track_state;
alter table layout.location_track_version
  alter column state type layout.location_track_state
  using state::text::layout.location_track_state;

alter table layout.location_track
  add constraint location_track_unique_official_name
    exclude (track_number_id with =, name with =)
    where (state != 'DELETED' and not draft)
    deferrable initially deferred;

alter table layout.location_track enable trigger version_row_trigger;
alter table layout.location_track enable trigger version_update_trigger;
