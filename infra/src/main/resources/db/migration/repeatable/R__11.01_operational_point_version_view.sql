drop view if exists layout.operational_point_version_view;
create view layout.operational_point_version_view as
select
  op.id,
  op.draft,
  op.design_id,
  op.version,
  op.layout_context_id,
  op.design_asset_state,
  op.origin_design_id,
  coalesce(op.name, rop.name) as name,
  coalesce(op.abbreviation, rop.abbreviation) as abbreviation,
  coalesce(op.uic_code, nullif(rop.uic_code, '')) as uic_code,
  coalesce(op.type, rop.type) as type,
  coalesce(op.location, rop.location) as location,
  op.state,
  op.rinf_type,
  op.polygon,
  op.origin,
  op.ratko_operational_point_version,
  op.deleted,
  op.change_user,
  op.change_time,
  op.expiry_time,
  op.rinf_code_generated,
  op.rinf_code_override
  from layout.operational_point_version op
    left join layout.operational_point_external_id ext_id
              on op.origin = 'RATKO' and op.id = ext_id.id and ext_id.layout_context_id = 'main_official'
    left join integrations.ratko_operational_point_version rop
              on ext_id.external_id = rop.external_id and op.ratko_operational_point_version = rop.version
;
