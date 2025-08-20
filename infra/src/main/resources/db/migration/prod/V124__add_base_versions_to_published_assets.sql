-- Simplify publication table composite key column names to match version tables
-- As elsewhere in Geoviite, the pattern is now that the item's own id is just 'id', not 'item_id'

alter table publication.track_number
  rename column track_number_id to id;
alter table publication.track_number
  rename column track_number_version to version;

alter table publication.reference_line
  rename column reference_line_id to id;
alter table publication.reference_line
  rename column reference_line_version to version;

alter table publication.km_post
  rename column km_post_id to id;
alter table publication.km_post
  rename column km_post_version to version;

alter table publication.location_track
  rename column location_track_id to id;
alter table publication.location_track
  rename column location_track_version to version;

alter table publication.switch
  rename column switch_id to id;
alter table publication.switch
  rename column switch_version to version;

-- Add new base-version columns to ease finding it, since the base version can be of a different context
-- Note, the asset ID can not change, so there is no base_id
-- Base version is nullable, since a new asset can't have a base version

alter table publication.track_number
  add column base_version           int     null,
  add column base_layout_context_id varchar null,
  add constraint publication_base_track_number_version_fkey
    foreign key (id, base_layout_context_id, base_version)
      references layout.track_number_version (id, layout_context_id, version);

alter table publication.reference_line
  add column base_version           int     null,
  add column base_layout_context_id varchar null,
  add constraint publication_base_reference_line_version_fkey
    foreign key (id, base_layout_context_id, base_version)
      references layout.reference_line_version (id, layout_context_id, version);

alter table publication.km_post
  add column base_version           int     null,
  add column base_layout_context_id varchar null,
  add constraint publication_base_km_post_version_fkey
    foreign key (id, base_layout_context_id, base_version)
      references layout.km_post_version (id, layout_context_id, version);

alter table publication.location_track
  add column base_version           int     null,
  add column base_layout_context_id varchar null,
  add constraint publication_base_location_track_version_fkey
    foreign key (id, base_layout_context_id, base_version)
      references layout.location_track_version (id, layout_context_id, version);

alter table publication.switch
  add column base_version           int     null,
  add column base_layout_context_id varchar null,
  add constraint publication_base_switch_version_fkey
    foreign key (id, base_layout_context_id, base_version)
      references layout.switch_version (id, layout_context_id, version);

update publication.location_track
set
  base_version = version,
  base_layout_context_id = layout_context_id
  where direct_change = false;

-- For non-direct changes, we can set the base version to the current version, as it didn't change in that publication
-- For the others, we don't actually have any designs yet, so we can simply set the base version to
-- be the previous version of the same (always main) context. In the future, this must be set when
-- saving the publication

update publication.track_number publication_asset
set
  base_version = base_version.version,
  base_layout_context_id = base_version.layout_context_id
  from (
    select id, layout_context_id, version
      from layout.track_number_version
      where deleted = false
  ) base_version
  where publication_asset.id = base_version.id
    and publication_asset.layout_context_id = base_version.layout_context_id
    and publication_asset.version = base_version.version + 1
    and publication_asset.direct_change = true;

update publication.reference_line publication_asset
set
  base_version = base_version.version,
  base_layout_context_id = base_version.layout_context_id
  from (
    select id, layout_context_id, version
      from layout.reference_line_version
      where deleted = false
  ) base_version
  where publication_asset.id = base_version.id
    and publication_asset.layout_context_id = base_version.layout_context_id
    and publication_asset.version = base_version.version + 1;

update publication.km_post publication_asset
set
  base_version = base_version.version,
  base_layout_context_id = base_version.layout_context_id
  from (
    select id, layout_context_id, version
      from layout.km_post_version
      where deleted = false
  ) base_version
  where publication_asset.id = base_version.id
    and publication_asset.layout_context_id = base_version.layout_context_id
    and publication_asset.version = base_version.version + 1;

update publication.track_number
set
  base_version = version,
  base_layout_context_id = layout_context_id
  where direct_change = false;

update publication.location_track publication_asset
set
  base_version = base_version.version,
  base_layout_context_id = base_version.layout_context_id
  from (
    select id, layout_context_id, version
      from layout.location_track_version
      where deleted = false
  ) base_version
  where publication_asset.id = base_version.id
    and publication_asset.layout_context_id = base_version.layout_context_id
    and publication_asset.version = base_version.version + 1
    and publication_asset.direct_change = true;

update publication.switch
set
  base_version = version,
  base_layout_context_id = layout_context_id
  where direct_change = false;

update publication.switch publication_asset
set
  base_version = base_version.version,
  base_layout_context_id = base_version.layout_context_id
  from (
    select id, layout_context_id, version
      from layout.switch_version
      where deleted = false
  ) base_version
  where publication_asset.id = base_version.id
    and publication_asset.layout_context_id = base_version.layout_context_id
    and publication_asset.version = base_version.version + 1
    and publication_asset.direct_change = true;
