create table layout.vertical_geometry_listing_file
(
  id          int primary key generated always as (1) stored,
  name        varchar(100) not null,
  content     varchar      not null,
  change_time timestamptz  not null,
  change_user varchar(30)  not null
);
