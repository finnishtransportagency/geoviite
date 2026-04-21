alter table layout.operational_point
  disable trigger version_update_trigger;
alter table layout.operational_point
  disable trigger version_row_trigger;

update layout.operational_point op
set ratko_operational_point_version = ropv.version
  from layout.operational_point_external_id ext_id
    join integrations.ratko_operational_point_version ropv
         on ext_id.external_id = ropv.external_id
  where op.id = ext_id.id
    and op.origin = 'RATKO'
    and op.state = 'DELETED'
    and op.change_time >= ropv.change_time
    and (ropv.expiry_time is null or op.change_time < ropv.expiry_time)
    and op.ratko_operational_point_version is distinct from ropv.version;

update layout.operational_point_version opv
set ratko_operational_point_version = ropv.version
  from layout.operational_point_external_id ext_id
    join integrations.ratko_operational_point_version ropv
         on ext_id.external_id = ropv.external_id
  where opv.id = ext_id.id
    and opv.origin = 'RATKO'
    and opv.state = 'DELETED'
    and opv.change_time >= ropv.change_time
    and (ropv.expiry_time is null or opv.change_time < ropv.expiry_time)
    and opv.ratko_operational_point_version is distinct from ropv.version;

alter table layout.operational_point
  enable trigger version_update_trigger;
alter table layout.operational_point
  enable trigger version_row_trigger;
