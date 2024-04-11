alter table geometry.plan
  disable trigger version_update_trigger;
alter table geometry.plan
  disable trigger version_row_trigger;

alter table geometry.plan
  add column track_number varchar(30);
alter table geometry.plan_version
  add column track_number varchar(30);
update geometry.plan set track_number = tn.number from layout.track_number tn where tn.id = plan.track_number_id;
update geometry.plan_version
set track_number = tn.number
  from layout.track_number tn
  where tn.id = plan_version.track_number_id;
alter table geometry.plan
  drop column track_number_id;
alter table geometry.plan_version
  drop column track_number_id;

alter table geometry.plan
  enable trigger version_update_trigger;
alter table geometry.plan
  enable trigger version_row_trigger;
