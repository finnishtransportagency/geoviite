create type geometry.plan_phase as enum ('RAILWAY_PLAN' , 'RAILWAY_CONSTRUCTION_PLAN', 'RENOVATION_PLAN' ,
  'ENHANCED_RENOVATION_PLAN' , 'MAINTENANCE','NEW_INVESTMENT' , 'REMOVED_FROM_USE');

create type geometry.plan_decision as enum ('APPROVED_PLAN' , 'UNDER_CONSTRUCTION', 'IN_USE');

create table geometry.plan
(
  id                         int primary key generated always as identity,
  track_number_id            int                               null,
  track_number_description   varchar(100)                      not null,
  plan_project_id            int                               not null,
  plan_author_id             int                               null,
  plan_application_id        int                               not null,
  plan_time                  timestamptz                       null,
  upload_time                timestamptz                       not null,
  linear_unit                common.linear_unit                not null,
  direction_unit             common.angular_unit               not null,
  srid                       int                               null,
  coordinate_system_name     varchar                           null,
  vertical_coordinate_system common.vertical_coordinate_system null,
  source                     geometry.plan_source              not null,
  oid                        varchar(100)                      null,
  plan_phase                 geometry.plan_phase               null,
  plan_decision              geometry.plan_decision            null,
  measurement_method         common.measurement_method         null,
  message                    varchar(250)                      null,
  linked_as_plan_id          int                               null,
  bounding_polygon           postgis.geometry(polygon, 3067)   null
);

select common.add_metadata_columns('geometry', 'plan');
comment on table geometry.plan is 'Geometry Plan.';
