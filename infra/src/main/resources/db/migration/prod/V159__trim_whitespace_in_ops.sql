-- trim names in layout operational_point and operational_point_version
alter table layout.operational_point
  disable trigger version_update_trigger;
alter table layout.operational_point
  disable trigger version_row_trigger;

update layout.operational_point_version set name = trim(name) where trim(name) <> name;

update layout.operational_point
set name = operational_point_version.name
  from layout.operational_point_version
  where operational_point.id = operational_point_version.id;

alter table layout.operational_point
  enable trigger version_update_trigger;
alter table layout.operational_point
  enable trigger version_row_trigger;

-- trim names in integrations ratko_operational_point_version
update integrations.ratko_operational_point_version set name = trim(name) where trim(name) <> name;
