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
  old.state as old_state
  from layout.km_post_version
    left join lateral (
    select state
      from layout.km_post_version old
      where old.id = km_post_version.id
        and old.layout_context_id = km_post_version.layout_context_id
        and old.version < km_post_version.version
      order by old.version desc
      limit 1
    ) old on (true)
  where not draft
);
