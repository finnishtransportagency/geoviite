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
  old.state as old_state
  from layout.track_number_version
    left join lateral (
      select state
      from layout.track_number_version old
      where old.id = track_number_version.id
        and old.layout_context_id = track_number_version.layout_context_id
        and old.version < track_number_version.version
      order by old.version desc
      limit 1
    ) old on (true)
  where not draft
);
