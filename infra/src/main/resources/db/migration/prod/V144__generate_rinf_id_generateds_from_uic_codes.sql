update layout.operational_point_id
set
  rinf_id_generated = 'FI' || lpad(coalesce(op.uic_code, rop.uic_code)::text, 5, '0')
  from layout.operational_point op
    left join layout.operational_point_external_id ext_id
              on op.origin = 'RATKO' and op.id = ext_id.id and ext_id.layout_context_id = 'main_official'
    left join integrations.ratko_operational_point_version rop
              on ext_id.external_id = rop.external_id and op.ratko_operational_point_version = rop.version
  where operational_point_id.id = op.id
    and not op.draft
    and op.design_id is null
    and coalesce(op.type, rop.type) is distinct from 'OLP'
    -- idempotency
    and operational_point_id.rinf_id_generated is null
    and op.id not in (11, 388);
