alter table layout.switch
  add constraint switch_unique_official_name
    exclude (name with =)
    where (state_category != 'NOT_EXISTING' and not draft)
    deferrable initially deferred;

alter table layout.location_track
  add constraint location_track_unique_official_name
    exclude (track_number_id with =, name with =)
    where (state != 'DELETED' and not draft)
    deferrable initially deferred;
