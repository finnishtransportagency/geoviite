with plan_with_lost_tracknumber as (
  select
    plan.id,
    max(intact.version) as last_intact_version
    from geometry.plan left join geometry.plan_version intact on intact.id = plan.id
    where plan.track_number_id is null
      and intact.track_number_id is not null
      and exists(select 1 from layout.track_number where id = intact.track_number_id)
    group by plan.id
)
update geometry.plan
set track_number_id = last_known_data.track_number_id
  from (
    select
      plan.id,
      intact.track_number_id
      from plan_with_lost_tracknumber plan
        left join geometry.plan_version intact on intact.id = plan.id and intact.version = plan.last_intact_version
  ) as last_known_data
  where plan.id = last_known_data.id;
