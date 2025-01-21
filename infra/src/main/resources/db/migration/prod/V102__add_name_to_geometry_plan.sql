alter table geometry.plan
  disable trigger version_update_trigger;
alter table geometry.plan
  disable trigger version_row_trigger;

alter table geometry.plan
  add column name varchar(100);

alter table geometry.plan_version
  add column name varchar(100);

update geometry.plan
set name = regexp_replace(
    (select name from geometry.plan_file where plan_file.plan_id=plan.id limit 1),
    -- Remove the "B" letter from the start and all ".(1 to 3 letters)" patterns from the end, e.g. ".tg.xml"
    '^B|(\.[a-z]{1,3})*$',
    '',
    'g'
           )
  where true;

update geometry.plan_version
set name = regexp_replace(
    (select name from geometry.plan_file where plan_file.plan_id=plan_version.id limit 1),
    -- Remove the "B" letter from the start and all ".(1 to 3 letters)" patterns from the end, e.g. ".tg.xml"
    '^B|(\.[a-z]{1,3})*$',
    '',
    'g'
           )
  where true;

alter table geometry.plan
  alter column name set not null;
alter table geometry.plan
  enable trigger version_update_trigger;
alter table geometry.plan
  enable trigger version_row_trigger;

alter table geometry.plan_version
  alter column name set not null;
