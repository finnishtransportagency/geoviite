-- trim names in layout operational_point and operational_point_version
alter table layout.operational_point
  disable trigger version_update_trigger;
alter table layout.operational_point
  disable trigger version_row_trigger;

update layout.operational_point_version set name = trim(name) where trim(name) <> name;

update layout.operational_point
set name = operational_point_version.name
  from layout.operational_point_version
  where operational_point.id = operational_point_version.id
    and operational_point.layout_context_id = operational_point_version.layout_context_id
    and operational_point.version = operational_point_version.version;

alter table layout.operational_point
  enable trigger version_update_trigger;
alter table layout.operational_point
  enable trigger version_row_trigger;

-- trim names in integrations ratko_operational_point and ratko_operational_point_version
alter table integrations.ratko_operational_point
  disable trigger version_update_trigger;
alter table integrations.ratko_operational_point
  disable trigger version_row_trigger;

update integrations.ratko_operational_point_version set name = trim(name) where trim(name) <> name;

update integrations.ratko_operational_point
set name = ratko_operational_point_version.name
  from integrations.ratko_operational_point_version
  where ratko_operational_point.external_id = ratko_operational_point_version.external_id
    and ratko_operational_point.version = ratko_operational_point_version.version;

alter table integrations.ratko_operational_point
  enable trigger version_update_trigger;
alter table integrations.ratko_operational_point
  enable trigger version_row_trigger;
