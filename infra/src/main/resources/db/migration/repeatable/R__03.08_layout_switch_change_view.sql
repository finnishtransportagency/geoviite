drop view if exists layout.switch_change_view;
create view layout.switch_change_view as
(
select
  switch_version.id,
  switch_version.change_time,
  switch_version.name,
  switch_version.state_category,
  switch_version.version,
  switch_version.change_user,
  lag(switch_version.state_category)
      over (partition by switch_version.id order by switch_version.version) as old_state_category
  from layout.switch_version
  where layout_context_id = 'main_official'
);
