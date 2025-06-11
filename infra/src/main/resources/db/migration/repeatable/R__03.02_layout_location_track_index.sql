drop index if exists layout.layout_location_track_track_number;
create index layout_location_track_track_number on layout.location_track (track_number_id);

drop index if exists layout.layout_location_track_alignment;

drop index if exists layout.layout_location_track_start_switch;
create index layout_location_track_start_switch on layout.location_track (start_switch_id);
drop index if exists layout.layout_location_track_end_switch;
create index layout_location_track_end_switch on layout.location_track (end_switch_id);
