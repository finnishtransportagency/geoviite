create table layout.km_post
(
  id                  int primary key generated always as identity,
  track_number_id     int                           not null,
  geometry_km_post_id int                           null,
  km_number           varchar(6)                    not null,
  location            postgis.geometry(point, 3067) null,
  state               layout.state                  not null,
  draft               boolean                       not null,
  draft_of_km_post_id int                           null
);

select common.add_metadata_columns('layout', 'km_post');
comment on table layout.km_post is 'Layout KM-post: reference point for switching between track kilometers.';
