alter table geometry.plan
  disable trigger version_update_trigger;
alter table geometry.plan
  disable trigger version_row_trigger;

create type geometry.plan_applicability as enum ('PLANNING', 'MAINTENANCE', 'STATISTICS');

alter table geometry.plan
  add column plan_applicability geometry.plan_applicability;

alter table geometry.plan_version
  add column plan_applicability geometry.plan_applicability;

alter table geometry.plan
  enable trigger version_update_trigger;
alter table geometry.plan
  enable trigger version_row_trigger;
