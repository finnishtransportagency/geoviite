-- increment this to force re-execution: 1
drop view if exists layout.operational_point_change_view;
create view layout.operational_point_change_view as
(
select
  operational_point_version.id,
  operational_point_version.layout_context_id,
  operational_point_version.change_time,
  operational_point_version.change_user,
  operational_point_version.state,
  operational_point_version.version,
  operational_point_version.name,
  old.state as old_state
  from layout.operational_point_version
    left join lateral (
    select state
      from layout.operational_point_version old
      where old.id = operational_point_version.id
        and old.layout_context_id = operational_point_version.layout_context_id
        and old.version < operational_point_version.version
      order by old.version desc
      limit 1
    ) old on (true)
  where not draft
);
