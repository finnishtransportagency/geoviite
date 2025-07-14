-- increment this to force re-execution: 1
drop view if exists layout.switch_change_view;
create view layout.switch_change_view as
(
select
  switch_version.id,
  switch_version.layout_context_id,
  switch_version.change_time,
  switch_version.name,
  switch_version.state_category,
  switch_version.version,
  switch_version.change_user,
  old.state_category as old_state_category
  from layout.switch_version
    left join lateral (
    select state_category
      from layout.switch_version old
      where old.id = switch_version.id
        and old.layout_context_id = switch_version.layout_context_id
        and old.version < switch_version.version
      order by old.version desc
      limit 1
    ) old on (true)
  where not draft
);
