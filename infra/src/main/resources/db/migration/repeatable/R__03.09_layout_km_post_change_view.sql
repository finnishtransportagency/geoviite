-- increment this to force re-execution: 1
drop view if exists layout.km_post_change_view;
create view layout.km_post_change_view as
(
select
  km_post_version.id,
  km_post_version.layout_context_id,
  km_post_version.change_time,
  km_post_version.track_number_id,
  km_post_version.change_user,
  km_post_version.state,
  km_post_version.version,
  km_number,
  lag(km_post_version.state)
      over (partition by km_post_version.id, design_id order by km_post_version.version) as old_state
  from layout.km_post_version
  where not draft
);
