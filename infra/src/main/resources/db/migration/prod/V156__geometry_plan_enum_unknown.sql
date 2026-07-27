-- Add UNKNOWN sentinel to each nullable plan enum type.
-- Postgres enums are append-only; UNKNOWN is added as a new value.
-- NOTE: Postgres forbids referencing a newly-added enum value in the same transaction.
-- Data migration and NOT NULL constraints are in V157.
alter type geometry.plan_phase add value 'UNKNOWN';
alter type geometry.plan_decision add value 'UNKNOWN';
alter type common.measurement_method add value 'UNKNOWN';
alter type common.elevation_measurement_method add value 'UNKNOWN';
alter type geometry.plan_quality add value 'UNKNOWN';
