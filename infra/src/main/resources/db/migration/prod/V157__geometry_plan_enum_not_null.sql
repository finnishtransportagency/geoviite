-- Migrate nulls and enforce NOT NULL on plan enum columns.
-- Runs in a separate transaction from V156 because Postgres forbids referencing
-- a newly-added enum value (ALTER TYPE ... ADD VALUE) in the same transaction.

-- Migrate nulls in the version table first (no triggers involved there).
update geometry.plan_version set plan_phase = 'UNKNOWN'               where plan_phase is null;
update geometry.plan_version set plan_decision = 'UNKNOWN'            where plan_decision is null;
update geometry.plan_version set measurement_method = 'UNKNOWN'       where measurement_method is null;
update geometry.plan_version set elevation_measurement_method = 'UNKNOWN' where elevation_measurement_method is null;
update geometry.plan_version set quality = 'UNKNOWN'                  where quality is null;

-- Migrate nulls in the current table.
-- Disable version triggers so the NULL→UNKNOWN fix does not create spurious version rows.
alter table geometry.plan disable trigger version_update_trigger;
alter table geometry.plan disable trigger version_row_trigger;

update geometry.plan set plan_phase = 'UNKNOWN'               where plan_phase is null;
update geometry.plan set plan_decision = 'UNKNOWN'            where plan_decision is null;
update geometry.plan set measurement_method = 'UNKNOWN'       where measurement_method is null;
update geometry.plan set elevation_measurement_method = 'UNKNOWN' where elevation_measurement_method is null;
update geometry.plan set quality = 'UNKNOWN'                  where quality is null;

alter table geometry.plan enable trigger version_update_trigger;
alter table geometry.plan enable trigger version_row_trigger;

-- Enforce NOT NULL now that no nulls remain.
alter table geometry.plan alter column plan_phase set not null;
alter table geometry.plan alter column plan_decision set not null;
alter table geometry.plan alter column measurement_method set not null;
alter table geometry.plan alter column elevation_measurement_method set not null;
alter table geometry.plan alter column quality set not null;

alter table geometry.plan_version alter column plan_phase set not null;
alter table geometry.plan_version alter column plan_decision set not null;
alter table geometry.plan_version alter column measurement_method set not null;
alter table geometry.plan_version alter column elevation_measurement_method set not null;
alter table geometry.plan_version alter column quality set not null;
