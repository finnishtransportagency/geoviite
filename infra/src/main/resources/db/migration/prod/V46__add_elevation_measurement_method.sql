create type common.elevation_measurement_method as enum (
  'TOP_OF_SLEEPER',
  'TOP_OF_RAIL'
);

alter table geometry.plan
  add column elevation_measurement_method common.elevation_measurement_method default 'TOP_OF_SLEEPER';

alter table geometry.plan_version
  add column elevation_measurement_method common.elevation_measurement_method default 'TOP_OF_SLEEPER';
