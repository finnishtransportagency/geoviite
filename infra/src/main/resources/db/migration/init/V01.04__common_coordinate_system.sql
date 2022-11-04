create table common.coordinate_system
(
  srid    int primary key not null references postgis.spatial_ref_sys (srid),
  name    varchar(100)     not null,
  aliases varchar(100)[]   not null
);

select common.add_table_metadata('common', 'coordinate_system');
comment on table common.coordinate_system is
  'Coordinate reference system: subset of ones supported by postgis.spatial_ref_sys.';
