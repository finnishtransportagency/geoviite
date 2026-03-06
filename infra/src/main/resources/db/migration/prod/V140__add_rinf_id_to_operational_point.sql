alter table layout.operational_point
  disable trigger version_update_trigger;
alter table layout.operational_point
  disable trigger version_row_trigger;


create sequence if not exists layout.rinf_id_seq
  start with 100000
  increment by 1;

create or replace function layout.generate_rinf_id()
  returns varchar(12)
  language sql
as
$$
select 'FI' || nextval('layout.rinf_id_seq')::text;
$$;

alter table layout.operational_point
  add column rinf_id_generated varchar(12);
alter table layout.operational_point
  add column rinf_id_override varchar(12);

alter table layout.operational_point_version
  add column rinf_id_generated varchar(12);
alter table layout.operational_point_version
  add column rinf_id_override varchar(12);

alter table layout.operational_point
  enable trigger version_update_trigger;
alter table layout.operational_point
  enable trigger version_row_trigger;

create unique index operational_point_unique_rinf_id_generated_official
  on layout.operational_point (rinf_id_generated)
  where rinf_id_generated is not null and not draft;

create unique index operational_point_unique_rinf_id_generated_draft
  on layout.operational_point (rinf_id_generated)
  where rinf_id_generated is not null and draft;
