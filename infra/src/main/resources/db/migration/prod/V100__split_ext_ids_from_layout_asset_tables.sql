create table layout.track_number_external_id
(
  id                integer     not null,
  layout_context_id text        not null,
  design_id         integer,
  external_id       varchar(50) not null,
  constraint track_number_external_id_pkey primary key (id, layout_context_id),
  constraint track_number_external_id_id_fkey foreign key (id) references layout.track_number_id (id),
  constraint track_number_design_id_fkey foreign key (design_id) references layout.design (id),
  constraint track_number_layout_context_check check (layout_context_id = layout.layout_context_id(design_id, false)),
  constraint track_number_external_id_unique unique (external_id)
);

insert into layout.track_number_external_id (
  select id, layout_context_id, design_id, external_id from layout.track_number where external_id is not null and not draft
);
select common.add_table_metadata('layout', 'track_number_external_id');
create index track_number_external_id_version_change_time_ix on layout.track_number_external_id (change_time);

alter table layout.track_number
  drop column external_id;
alter table layout.track_number_version
  drop column external_id;

alter table layout.location_track drop constraint location_track_external_id_unique;
create table layout.location_track_external_id
(
  id                integer     not null,
  layout_context_id text        not null,
  design_id         integer,
  external_id       varchar(50) not null,
  constraint location_track_external_id_pkey primary key (id, layout_context_id),
  constraint location_track_external_id_id_fkey foreign key (id) references layout.location_track_id (id),
  constraint location_track_design_id_fkey foreign key (design_id) references layout.design (id),
  constraint location_track_layout_context_check check (layout_context_id = layout.layout_context_id(design_id, false)),
  constraint location_track_external_id_unique unique (external_id)
);

insert into layout.location_track_external_id (
  select id, layout_context_id, design_id, external_id from layout.location_track where external_id is not null and not draft
);
select common.add_table_metadata('layout', 'location_track_external_id');
create index location_track_external_id_version_change_time_ix on layout.location_track_external_id (change_time);

alter table layout.location_track
  drop column external_id;
alter table layout.location_track_version
  drop column external_id;

alter table layout.switch drop constraint switch_external_id_unique;
create table layout.switch_external_id
(
  id                integer     not null,
  layout_context_id text        not null,
  design_id         integer,
  external_id       varchar(50) not null,
  constraint switch_external_id_pkey primary key (id, layout_context_id),
  constraint switch_external_id_id_fkey foreign key (id) references layout.switch_id (id),
  constraint switch_design_id_fkey foreign key (design_id) references layout.design (id),
  constraint switch_layout_context_check check (layout_context_id = layout.layout_context_id(design_id, false)),
  constraint switch_external_id_unique unique (external_id)
);

insert into layout.switch_external_id (
  select id, layout_context_id, design_id, external_id from layout.switch where external_id is not null and not draft
);
select common.add_table_metadata('layout', 'switch_external_id');
create index switch_external_id_version_change_time_ix on layout.switch_external_id (change_time);


alter table layout.switch
  drop column external_id;
alter table layout.switch_version
  drop column external_id;
