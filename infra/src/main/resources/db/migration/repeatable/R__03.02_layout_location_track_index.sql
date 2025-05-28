drop index if exists layout.layout_location_track_track_number;
create index layout_location_track_track_number on layout.location_track (track_number_id);

drop index if exists layout.layout_location_track_alignment;
