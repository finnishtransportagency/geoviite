create table geometry.km_post
(
  id              int primary key generated always as identity,
  track_number_id int                     null,
  km_post_index   int                     not null,
  plan_id         int                     not null,
  sta_back        decimal(13, 6)          null,
  sta_ahead       decimal(13, 6)          not null,
  sta_internal    decimal(13, 6)          not null,
  km_number       varchar(6)              null,
  description     varchar(6)              not null,
  location        postgis.geometry(point) null,
  state           geometry.plan_state     null
);

comment on table geometry.km_post is 'Geometry KM-post: reference point for switching between track kilometers.';
