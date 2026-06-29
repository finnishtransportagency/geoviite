insert into common.oid_type values ('DESIGN');
insert into common.oid_sequence
  (group_number, number, type, service_oid, state)
  values
    (1, 2, 'DESIGN', '1.2.246.578.13', 'ACTIVE');

alter table layout.design
  disable trigger version_update_trigger;
alter table layout.design
  disable trigger version_row_trigger;

alter table layout.design
  add column external_id varchar(50);
alter table layout.design_version
  add column external_id varchar(50);

update layout.design set external_id = common.generate_oid('DESIGN') where true;
update layout.design_version
set external_id = design.external_id
  from layout.design
  where design.id = design_version.id;

alter table layout.design alter column external_id set not null;
alter table layout.design_version alter column external_id set not null;

alter table layout.design
  enable trigger version_update_trigger;
alter table layout.design
  enable trigger version_row_trigger;
