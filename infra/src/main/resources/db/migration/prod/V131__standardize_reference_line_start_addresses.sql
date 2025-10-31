alter table layout.reference_line
  disable trigger version_update_trigger,
  disable trigger version_row_trigger;

-- Update all reference line version rows to use the normalized format for start_address
with parsed_versions as (
  select
    id,
    layout_context_id,
    version,
    start_address,
    (regexp_matches(start_address, '^([0-9]{4}[A-Z]{0,2})\+([0-9]{4}(?:\.\d+)?)$'))[1] as km_part,
    (regexp_matches(start_address, '^([0-9]{4}[A-Z]{0,2})\+([0-9]{4}(?:\.\d+)?)$'))[2] as meters_part
    from layout.reference_line_version
),
  normalized_versions as (
    select
      pv.id,
      pv.layout_context_id,
      pv.version,
      pv.start_address,
      pv.km_part || '+' || to_char(round(pv.meters_part::numeric, 3), 'FM0000.000') as normalized_start_address
      from parsed_versions pv
  )
update layout.reference_line_version v
set
  start_address = nv.normalized_start_address
  from normalized_versions nv
  where v.id = nv.id
    and v.layout_context_id = nv.layout_context_id
    and v.version = nv.version
    and v.start_address != nv.normalized_start_address;

-- Update the main table to match the latest version row
update layout.reference_line rl
set start_address = v.start_address
  from layout.reference_line_version v
  where rl.id = v.id and rl.layout_context_id = v.layout_context_id and rl.version = v.version;

alter table layout.reference_line
  enable trigger version_update_trigger,
  enable trigger version_row_trigger;
