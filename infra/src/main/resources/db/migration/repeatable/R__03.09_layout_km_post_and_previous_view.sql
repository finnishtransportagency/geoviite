drop view if exists layout.km_post_and_previous_view;
create view layout.km_post_and_previous_view as
(
select
  km_post_version.id,
  km_post_version.change_time,
  km_post_version.track_number_id,
  km_post_version.change_user,
  km_post_version.state,
  km_post_version.version,
  km_number,
      lag(km_post_version.state)
      over (partition by km_post_version.id order by km_post_version.version) as old_state
from layout.km_post_version
where draft = false
);
