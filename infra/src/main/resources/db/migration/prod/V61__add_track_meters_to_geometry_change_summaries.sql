-- Set the summaries to be recalculated
delete from publication.location_track_geometry_change_summary;
update publication.location_track set geometry_change_summary_computed = not direct_change;

alter table publication.location_track_geometry_change_summary
  add column start_km_m decimal not null,
  add column end_km_m   decimal not null;
