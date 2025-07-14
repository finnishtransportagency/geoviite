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
  old.state as old_state
  from layout.location_track_version
    left join lateral (
      select state
      from layout.location_track_version old
      where old.id = location_track_version.id
        and old.layout_context_id = location_track_version.layout_context_id
        and old.version < location_track_version.version
      order by old.version desc
      limit 1
      ) old on (true)
  where not draft
);
