-- rename Ratko operational points out of the way

alter index layout.operating_point_pkey rename to ratko_operational_point_pkey;
alter index layout.operating_point_location_ix rename to ratko_operational_point_location_ix;

alter table layout.operating_point
  rename to ratko_operational_point;
alter table layout.ratko_operational_point
  set schema integrations;

alter table layout.operating_point_version
  rename to ratko_operational_point_version;
alter table layout.ratko_operational_point_version
  set schema integrations;

drop function layout.get_operating_point_version(varchar);
select common.create_version_fetch_function('integrations', 'ratko_operational_point');

-- types and reference data
create table common.rinf_operational_point_type
(
  code      int         not null primary key,
  enum_name varchar(50) not null,
  constraint rinf_operational_point_type_name_uk unique (enum_name)
);

insert into common.rinf_operational_point_type
  (code, enum_name)
  values
    (10, 'STATION'),
    (20, 'SMALL_STATION'),
    (30, 'PASSENGER_TERMINAL'),
    (40, 'FREIGHT_TERMINAL'),
    (50, 'DEPOT_OR_WORKSHOP'),
    (60, 'TRAIN_TECHNICAL_SERVICES'),
    (70, 'PASSENGER_STOP'),
    (80, 'JUNCTION'),
    (90, 'BORDER_POINT'),
    (100, 'SHUNTING_YARD'),
    (110, 'TECHNICAL_CHANGE'),
    (120, 'SWITCH'),
    (130, 'PRIVATE_SIDING'),
    (140, 'DOMESTIC_BORDER_POINT'),
    (150, 'OVER_CROSSING')
;

alter type layout.operating_point_type rename to operational_point_type;

create type layout.operational_point_state as enum ('IN_USE', 'DELETED');

create type layout.operational_point_origin as enum ('RATKO', 'GEOVIITE');

-- new operational point table proper, as an ordinary layout object type

create table layout.operational_point_id
(
  id int not null primary key generated always as identity
);

create table layout.operational_point
(
  id                              int                             not null,
  draft                           boolean                         not null,
  design_id                       int,
  layout_context_id               text                            not null,
  design_asset_state              layout.design_asset_state,
  origin_design_id                int,
  name                            varchar(150),
  abbreviation                    varchar(20),
  uic_code                        varchar(20),
  type                            layout.operational_point_type,
  location                        postgis.geometry(Point, 3067),
  state                           layout.operational_point_state  not null,
  rinf_type                       varchar(50),
  polygon                         postgis.geometry(Polygon, 3067),
  origin                          layout.operational_point_origin not null,
  ratko_operational_point_version int,

  constraint operational_point_pkey primary key (id, layout_context_id),
  constraint operational_point_id_fkey foreign key (id) references layout.operational_point_id (id),
  constraint layout_context_id_check check (layout.layout_context_id(design_id, draft) = layout_context_id),
  constraint operational_point_rinf_type_fk foreign key (rinf_type) references common.rinf_operational_point_type (enum_name),
  constraint ratko_points_consistency_check check
    (case
       when origin = 'RATKO' then ratko_operational_point_version is not null
         and name is null
         and abbreviation is null
         and uic_code is null
         and location is null
         and type is null
       when origin = 'GEOVIITE' then ratko_operational_point_version is null
         and name is not null
       else false
     end
    )
);

create table layout.operational_point_external_id
(
  id                int         not null,
  layout_context_id text        not null,
  design_id         int,
  external_id       varchar(50) not null,
  plan_item_id      int,
  constraint operational_point_external_id_pkey primary key (id, layout_context_id),
  constraint layout_context_id_check check (layout.layout_context_id(design_id, false) = layout_context_id),
  constraint operational_point_external_id_id_fkey foreign key (id) references layout.operational_point_id (id)
);

select common.add_table_metadata('layout', 'operational_point');
select common.add_table_metadata('layout', 'operational_point_external_id');

insert into layout.operational_point_id (
  select
    from integrations.ratko_operational_point
);

insert into layout.operational_point
  (id, draft, design_id, layout_context_id,
   state, origin, ratko_operational_point_version)
  (
    select
      row_number() over (order by external_id),
      true,
      null,
      'main_draft',
      'IN_USE',
      'RATKO',
      version
      from integrations.ratko_operational_point
  );

insert into layout.operational_point_external_id
  (id, layout_context_id, design_id, external_id)
  (
    select row_number() over (order by external_id), 'main_official', null, external_id
      from integrations.ratko_operational_point
  );

alter table layout.operational_point
  add constraint operational_point_id_fk foreign key (id) references layout.operational_point_id (id);
