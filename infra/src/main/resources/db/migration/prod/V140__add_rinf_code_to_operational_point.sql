alter table layout.operational_point
  disable trigger version_update_trigger;
alter table layout.operational_point
  disable trigger version_row_trigger;


create sequence if not exists layout.rinf_code_seq
  start with 100000
  increment by 1;

create or replace function layout.generate_rinf_code()
  returns varchar(12)
  language sql
as
$$
select 'FI' || nextval('layout.rinf_code_seq')::text;
$$;

alter table layout.operational_point
  add column rinf_code_generated varchar(12);
alter table layout.operational_point
  add column rinf_code_override varchar(12);

alter table layout.operational_point_version
  add column rinf_code_generated varchar(12);
alter table layout.operational_point_version
  add column rinf_code_override varchar(12);

alter table layout.operational_point
  enable trigger version_update_trigger;
alter table layout.operational_point
  enable trigger version_row_trigger;
