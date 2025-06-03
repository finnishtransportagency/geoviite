update layout.location_track set description_base = trim(description_base);
update layout.location_track_version set description_base = trim(description_base);
update layout.location_track set name = trim(name);
update layout.location_track_version set name = trim(name);

update layout.track_number set number = trim(number);
update layout.track_number_version set number = trim(number);
update layout.track_number set description = trim(description);
update layout.track_number_version set description = trim(description);

update layout.switch set name = trim(name);
update layout.switch_version set name = trim(name);
