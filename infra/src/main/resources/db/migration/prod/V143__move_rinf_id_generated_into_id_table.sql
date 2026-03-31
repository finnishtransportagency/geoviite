drop trigger operational_point_rinf_id_generated_unique_check on layout.operational_point;

drop function layout.check_rinf_id_generated_uniqueness;

alter table layout.operational_point
  drop column rinf_id_generated;

alter table layout.operational_point_version
  drop column rinf_id_generated;

alter table layout.operational_point_id
  add column rinf_id_generated varchar(12),
  add constraint rinf_id_generated_uk unique (rinf_id_generated);
