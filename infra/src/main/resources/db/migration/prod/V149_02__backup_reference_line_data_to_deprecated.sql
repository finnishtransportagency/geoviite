-- Backup track_number, reference_line and associated alignment data to the deprecated schema.
-- Drops the old location track alignment backup tables (no longer useful for verification
-- as the location track model has changed significantly since they were created).

drop table deprecated.location_track_version_alignment;
drop table deprecated.alignment_version_segment;
drop table deprecated.alignment_version;

create table deprecated.alignment_version
(
  id            int                             not null,
  version       int                             not null,
  bounding_box  postgis.geometry(polygon, 3067) null,
  segment_count int                             not null,
  length        decimal(13, 6)                  not null,
  change_user   varchar(30)                     not null,
  change_time   timestamptz                     not null,
  deleted       boolean                         not null,

  primary key (id, version)
);

create table deprecated.track_number_version
(
  id                 int                       not null,
  layout_context_id  varchar                   not null,
  version            int                       not null,
  number             varchar(30)               not null,
  description        varchar(100)              not null,
  state              layout.state              not null,
  draft              boolean                   not null,
  design_asset_state layout.design_asset_state null,
  design_id          int                       null,
  origin_design_id   int                       null,
  change_user        varchar(30)               not null,
  change_time        timestamptz               not null,
  expiry_time        timestamptz               null,
  deleted            boolean                   not null,

  primary key (id, layout_context_id, version)
);

create table deprecated.reference_line_version
(
  id                 int                       not null,
  layout_context_id  varchar                   not null,
  version            int                       not null,
  track_number_id    int                       not null,
  alignment_id       int                       not null,
  alignment_version  int                       not null,
  start_address      varchar(20)               not null,
  draft              boolean                   not null,
  design_asset_state layout.design_asset_state null,
  design_id          int                       null,
  origin_design_id   int                       null,
  change_user        varchar(30)               not null,
  change_time        timestamptz               not null,
  expiry_time        timestamptz               null,
  deleted            boolean                   not null,

  primary key (id, layout_context_id, version),
  foreign key (alignment_id, alignment_version) references deprecated.alignment_version (id, version)
);

create table deprecated.alignment_version_segment
(
  alignment_id           int                    not null,
  alignment_version      int                    not null,
  segment_index          int                    not null,
  geometry_alignment_id  int                    null,
  geometry_element_index int                    null,
  start_m                decimal(13, 6)         not null,
  source_start_m         decimal(13, 6)         null,
  source                 layout.geometry_source not null,
  geometry_id            int                    not null references layout.segment_geometry (id),

  primary key (alignment_id, alignment_version, segment_index),
  foreign key (alignment_id, alignment_version) references deprecated.alignment_version (id, version)
);

-- Populate alignment_version first (referenced by reference_line_version)
insert into deprecated.alignment_version
  (id, version, bounding_box, segment_count, length, change_user, change_time, deleted)
select
  av.id,
  av.version,
  av.bounding_box,
  av.segment_count,
  av.length,
  av.change_user,
  av.change_time,
  av.deleted
  from layout.alignment_version av
  where exists(
    select 1
      from layout.reference_line_version rlv
      where rlv.alignment_id = av.id and rlv.alignment_version = av.version
  );

-- Populate track_number_version and reference_line_version (independent of each other)
insert into deprecated.track_number_version
  (id, layout_context_id, version, number, description, state, draft,
   design_asset_state, design_id, origin_design_id, change_user, change_time, expiry_time, deleted)
select
  id,
  layout_context_id,
  version,
  number,
  description,
  state,
  draft,
  design_asset_state,
  design_id,
  origin_design_id,
  change_user,
  change_time,
  expiry_time,
  deleted
  from layout.track_number_version;

insert into deprecated.reference_line_version
  (id, layout_context_id, version, track_number_id, alignment_id, alignment_version, start_address,
   draft, design_asset_state, design_id, origin_design_id, change_user, change_time, expiry_time, deleted)
select
  id,
  layout_context_id,
  version,
  track_number_id,
  alignment_id,
  alignment_version,
  start_address,
  draft,
  design_asset_state,
  design_id,
  origin_design_id,
  change_user,
  change_time,
  expiry_time,
  deleted
  from layout.reference_line_version;

-- Populate alignment_version_segment last (references alignment_version)
insert into deprecated.alignment_version_segment
  (alignment_id, alignment_version, segment_index, geometry_alignment_id, geometry_element_index,
   start_m, source_start_m, source, geometry_id)
select
  s.alignment_id,
  s.alignment_version,
  s.segment_index,
  s.geometry_alignment_id,
  s.geometry_element_index,
  s.start as start_m,
  s.source_start as source_start_m,
  s.source,
  s.geometry_id
  from layout.segment_version s
  where exists(
    select 1 from deprecated.alignment_version av where av.id = s.alignment_id and av.version = s.alignment_version
  );

-- Backup initial_segment_metadata
create table deprecated.initial_segment_metadata
(
  alignment_id  int not null,
  segment_index int not null,
  metadata_id   int not null,
  primary key (alignment_id, segment_index)
);

insert into deprecated.initial_segment_metadata
  (alignment_id, segment_index, metadata_id)
select alignment_id, segment_index, metadata_id
  from layout.initial_segment_metadata;
