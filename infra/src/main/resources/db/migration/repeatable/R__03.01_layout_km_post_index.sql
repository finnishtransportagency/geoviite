drop index if exists layout.layout_km_post_track_number_index;
create index layout_km_post_track_number_index on layout.km_post(track_number_id, km_number);

drop index if exists layout.layout_km_post_location_index;
create index layout_km_post_location_index on layout.km_post using gist (layout_location);
