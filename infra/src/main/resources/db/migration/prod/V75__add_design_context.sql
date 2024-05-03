create type layout.design_state as enum ('ACTIVE', 'DELETED', 'COMPLETED');

create table layout.design
(
  id                   int primary key generated always as identity,
  name                 text                not null,
  estimated_completion date                not null,
  plan_phase           geometry.plan_phase not null,
  design_state         layout.design_state not null
);
comment on table layout.design is 'Overlays for planned changes to the layout.';

select common.add_table_metadata('layout', 'design');

alter table layout.track_number
  add column design_id     integer,
  add constraint track_number_design_fk foreign key (design_id) references layout.design (id),
  add column design_row_id integer,
  add constraint track_number_design_row_fk foreign key (design_row_id) references layout.track_number (id);

alter table layout.track_number_version
  add column design_id     integer,
  add column design_row_id integer;

alter table layout.location_track
  add column design_id     integer,
  add constraint location_track_design_fk foreign key (design_id) references layout.design (id),
  add column design_row_id integer,
  add constraint location_track_design_row_fk foreign key (design_row_id) references layout.location_track (id);

alter table layout.location_track_version
  add column design_id     integer,
  add column design_row_id integer;

alter table layout.switch
  add column design_id     integer,
  add constraint switch_design_fk foreign key (design_id) references layout.design (id),
  add column design_row_id integer,
  add constraint switch_design_row_fk foreign key (design_row_id) references layout.switch (id);

alter table layout.switch_version
  add column design_id     integer,
  add column design_row_id integer;

alter table layout.reference_line
  add column design_id     integer,
  add constraint reference_line_design_fk foreign key (design_id) references layout.design (id),
  add column design_row_id integer,
  add constraint reference_line_design_row_fk foreign key (design_row_id) references layout.reference_line (id);

alter table layout.reference_line_version
  add column design_id     integer,
  add column design_row_id integer;

alter table layout.km_post
  add column design_id     integer,
  add constraint km_post_design_fk foreign key (design_id) references layout.design (id),
  add column design_row_id integer,
  add constraint km_post_design_row_fk foreign key (design_row_id) references layout.km_post (id);

alter table layout.km_post_version
  add column design_id     integer,
  add column design_row_id integer;
