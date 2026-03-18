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

alter table layout.operational_point_id
  add column rinf_id_generated varchar(12),
  add constraint rinf_id_generated_uk unique (rinf_id_generated);

alter table layout.operational_point
  add column rinf_id_override varchar(12);
alter table layout.operational_point_version
  add column rinf_id_override varchar(12);
