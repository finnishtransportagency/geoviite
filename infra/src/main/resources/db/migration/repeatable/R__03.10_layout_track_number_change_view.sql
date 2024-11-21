-- increment this to force re-execution: 1
drop view if exists layout.track_number_change_view;
create view layout.track_number_change_view as
(
select
  track_number_version.id,
  track_number_version.layout_context_id,
  track_number_version.change_time,
  track_number_version.number,
  track_number_version.change_user,
  track_number_version.state,
  track_number_version.version,
  lag(track_number_version.state)
      over (partition by track_number_version.id, design_id order by track_number_version.version) old_state
  from layout.track_number_version
  where not draft
);
