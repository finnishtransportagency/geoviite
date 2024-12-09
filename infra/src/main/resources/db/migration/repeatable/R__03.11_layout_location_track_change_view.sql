-- increment to force a rerun: 2

drop view if exists layout.location_track_change_view;
create view layout.location_track_change_view as
(
select
  location_track_version.id,
  location_track_version.layout_context_id,
  location_track_version.change_time,
  location_track_version.name,
  location_track_version.track_number_id,
  location_track_version.state,
  location_track_version.change_user,
  location_track_version.version,
  lag(location_track_version.state)
      over (partition by location_track_version.id, design_id order by location_track_version.version) old_state
  from layout.location_track_version
  where not draft
);
