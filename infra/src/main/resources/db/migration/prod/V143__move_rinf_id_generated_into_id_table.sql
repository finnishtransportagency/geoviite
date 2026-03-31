drop view if exists layout.operational_point_version_view;

alter table layout.operational_point
  drop column rinf_id_generated;

alter table layout.operational_point_version
  drop column rinf_id_generated;

alter table layout.operational_point_id
  add column rinf_id_generated varchar(12),
  add constraint rinf_id_generated_uk unique (rinf_id_generated);
