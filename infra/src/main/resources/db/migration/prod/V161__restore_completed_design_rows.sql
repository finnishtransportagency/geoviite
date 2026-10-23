-- Before GVT-3767, publishing a COMPLETED design-official row deleted it right away: the delete trigger wrote
-- version N+1 with deleted = true and expired version N. COMPLETED rows now stay live like CANCELLED ones.
-- This migration gives the old rows the same shape: version N becomes the live row again, the deletion
-- version N+1 is removed and N's expiry is cleared. Publication rows that referenced N+1 are re-pointed to N.
-- Child rows (edges, segments, joints, location track operational points) are keyed to N, so they need nothing.

-- track_number
-- Disable the triggers, so the restored row keeps its own version, change time and user
alter table layout.track_number
  disable trigger version_update_trigger;
alter table layout.track_number
  disable trigger version_row_trigger;

-- Find the chains to restore
create temporary table restore_track_number on commit drop as
select completed.id, completed.layout_context_id, completed.version
  from layout.track_number_version deletion
    join layout.track_number_version completed
         on completed.id = deletion.id
           and completed.layout_context_id = deletion.layout_context_id
           and completed.version = deletion.version - 1
  where deletion.design_id is not null
    and exists (select * from layout.design where design.id = deletion.design_id)
    and not deletion.draft
    and deletion.deleted
    and completed.design_asset_state = 'COMPLETED'
    and not completed.deleted
    and deletion.version = (
    select max(latest.version)
      from layout.track_number_version latest
      where latest.id = deletion.id
        and latest.layout_context_id = deletion.layout_context_id
  )
    and not exists (
    select *
      from layout.track_number live
      where live.id = deletion.id
        and live.layout_context_id = deletion.layout_context_id
  );

-- Put N back into the live table
insert into layout.track_number
  (id, number, description, state, draft, version, change_user, change_time, design_id,
   layout_context_id, origin_design_id, design_asset_state, start_address, bounding_box,
   segment_count, length)
select
  version_row.id, version_row.number, version_row.description, version_row.state, version_row.draft,
  version_row.version, version_row.change_user, version_row.change_time, version_row.design_id,
  version_row.layout_context_id, version_row.origin_design_id, version_row.design_asset_state,
  version_row.start_address, version_row.bounding_box, version_row.segment_count, version_row.length
  from layout.track_number_version version_row
    join restore_track_number restore using (id, layout_context_id, version);

-- Re-point publication references from N+1 to N before N+1 is deleted
update publication.track_number publication_row
set version = restore.version
  from restore_track_number restore
  where publication_row.id = restore.id
    and publication_row.layout_context_id = restore.layout_context_id
    and publication_row.version = restore.version + 1;

update publication.track_number publication_row
set base_version = restore.version
  from restore_track_number restore
  where publication_row.id = restore.id
    and publication_row.base_layout_context_id = restore.layout_context_id
    and publication_row.base_version = restore.version + 1;

-- Remove N+1 from history
delete
  from layout.track_number_version version_row
  using restore_track_number restore
  where version_row.id = restore.id
    and version_row.layout_context_id = restore.layout_context_id
    and version_row.version = restore.version + 1;

-- Clear N's expiry
update layout.track_number_version version_row
set expiry_time = null
  from restore_track_number restore
  where version_row.id = restore.id
    and version_row.layout_context_id = restore.layout_context_id
    and version_row.version = restore.version;

-- Re-enable the triggers
alter table layout.track_number
  enable trigger version_update_trigger;
alter table layout.track_number
  enable trigger version_row_trigger;

-- location_track
-- Disable the triggers, so the restored row keeps its own version, change time and user
alter table layout.location_track
  disable trigger version_update_trigger;
alter table layout.location_track
  disable trigger version_row_trigger;

-- Find the chains to restore
create temporary table restore_location_track on commit drop as
select completed.id, completed.layout_context_id, completed.version
  from layout.location_track_version deletion
    join layout.location_track_version completed
         on completed.id = deletion.id
           and completed.layout_context_id = deletion.layout_context_id
           and completed.version = deletion.version - 1
  where deletion.design_id is not null
    and exists (select * from layout.design where design.id = deletion.design_id)
    and not deletion.draft
    and deletion.deleted
    and completed.design_asset_state = 'COMPLETED'
    and not completed.deleted
    and deletion.version = (
    select max(latest.version)
      from layout.location_track_version latest
      where latest.id = deletion.id
        and latest.layout_context_id = deletion.layout_context_id
  )
    and not exists (
    select *
      from layout.location_track live
      where live.id = deletion.id
        and live.layout_context_id = deletion.layout_context_id
  );

-- Put N back into the live table
insert into layout.location_track
  (id, track_number_id, name, description_base, type, state, draft, duplicate_of_location_track_id,
   topological_connectivity, version, change_user, change_time, description_suffix, owner_id,
   design_id, layout_context_id, origin_design_id, design_asset_state, bounding_box, length,
   edge_count, segment_count, start_switch_id, end_switch_id, naming_scheme, name_specifier,
   name_free_text, description)
select
  version_row.id, version_row.track_number_id, version_row.name, version_row.description_base,
  version_row.type, version_row.state, version_row.draft, version_row.duplicate_of_location_track_id,
  version_row.topological_connectivity, version_row.version, version_row.change_user,
  version_row.change_time, version_row.description_suffix, version_row.owner_id,
  version_row.design_id, version_row.layout_context_id, version_row.origin_design_id,
  version_row.design_asset_state, version_row.bounding_box, version_row.length,
  version_row.edge_count, version_row.segment_count, version_row.start_switch_id,
  version_row.end_switch_id, version_row.naming_scheme, version_row.name_specifier,
  version_row.name_free_text, version_row.description
  from layout.location_track_version version_row
    join restore_location_track restore using (id, layout_context_id, version);

-- Re-point publication references from N+1 to N before N+1 is deleted
update publication.location_track publication_row
set version = restore.version
  from restore_location_track restore
  where publication_row.id = restore.id
    and publication_row.layout_context_id = restore.layout_context_id
    and publication_row.version = restore.version + 1;

update publication.location_track publication_row
set base_version = restore.version
  from restore_location_track restore
  where publication_row.id = restore.id
    and publication_row.base_layout_context_id = restore.layout_context_id
    and publication_row.base_version = restore.version + 1;

update publication.switch_location_tracks publication_row
set location_track_version = restore.version
  from restore_location_track restore
  where publication_row.location_track_id = restore.id
    and publication_row.location_track_layout_context_id = restore.layout_context_id
    and publication_row.location_track_version = restore.version + 1;

-- Remove N+1 from history
delete
  from layout.location_track_version version_row
  using restore_location_track restore
  where version_row.id = restore.id
    and version_row.layout_context_id = restore.layout_context_id
    and version_row.version = restore.version + 1;

-- Clear N's expiry
update layout.location_track_version version_row
set expiry_time = null
  from restore_location_track restore
  where version_row.id = restore.id
    and version_row.layout_context_id = restore.layout_context_id
    and version_row.version = restore.version;

-- Re-enable the triggers
alter table layout.location_track
  enable trigger version_update_trigger;
alter table layout.location_track
  enable trigger version_row_trigger;

-- switch
-- Disable the triggers, so the restored row keeps its own version, change time and user
alter table layout.switch
  disable trigger version_update_trigger;
alter table layout.switch
  disable trigger version_row_trigger;

-- Find the chains to restore
create temporary table restore_switch on commit drop as
select completed.id, completed.layout_context_id, completed.version
  from layout.switch_version deletion
    join layout.switch_version completed
         on completed.id = deletion.id
           and completed.layout_context_id = deletion.layout_context_id
           and completed.version = deletion.version - 1
  where deletion.design_id is not null
    and exists (select * from layout.design where design.id = deletion.design_id)
    and not deletion.draft
    and deletion.deleted
    and completed.design_asset_state = 'COMPLETED'
    and not completed.deleted
    and deletion.version = (
    select max(latest.version)
      from layout.switch_version latest
      where latest.id = deletion.id
        and latest.layout_context_id = deletion.layout_context_id
  )
    and not exists (
    select *
      from layout.switch live
      where live.id = deletion.id
        and live.layout_context_id = deletion.layout_context_id
  );

-- Put N back into the live table
insert into layout.switch
  (id, geometry_switch_id, name, switch_structure_id, state_category, trap_point, owner_id, draft,
   source, version, change_user, change_time, design_id, layout_context_id, origin_design_id,
   draft_oid, design_asset_state, operational_point_id)
select
  version_row.id, version_row.geometry_switch_id, version_row.name, version_row.switch_structure_id,
  version_row.state_category, version_row.trap_point, version_row.owner_id, version_row.draft,
  version_row.source, version_row.version, version_row.change_user, version_row.change_time,
  version_row.design_id, version_row.layout_context_id, version_row.origin_design_id,
  version_row.draft_oid, version_row.design_asset_state, version_row.operational_point_id
  from layout.switch_version version_row
    join restore_switch restore using (id, layout_context_id, version);

-- Re-point publication references from N+1 to N before N+1 is deleted
update publication.switch publication_row
set version = restore.version
  from restore_switch restore
  where publication_row.id = restore.id
    and publication_row.layout_context_id = restore.layout_context_id
    and publication_row.version = restore.version + 1;

update publication.switch publication_row
set base_version = restore.version
  from restore_switch restore
  where publication_row.id = restore.id
    and publication_row.base_layout_context_id = restore.layout_context_id
    and publication_row.base_version = restore.version + 1;

-- Remove N+1 from history
delete
  from layout.switch_version version_row
  using restore_switch restore
  where version_row.id = restore.id
    and version_row.layout_context_id = restore.layout_context_id
    and version_row.version = restore.version + 1;

-- Clear N's expiry
update layout.switch_version version_row
set expiry_time = null
  from restore_switch restore
  where version_row.id = restore.id
    and version_row.layout_context_id = restore.layout_context_id
    and version_row.version = restore.version;

-- Re-enable the triggers
alter table layout.switch
  enable trigger version_update_trigger;
alter table layout.switch
  enable trigger version_row_trigger;

-- km_post
-- Disable the triggers, so the restored row keeps its own version, change time and user
alter table layout.km_post
  disable trigger version_update_trigger;
alter table layout.km_post
  disable trigger version_row_trigger;

-- Find the chains to restore
create temporary table restore_km_post on commit drop as
select completed.id, completed.layout_context_id, completed.version
  from layout.km_post_version deletion
    join layout.km_post_version completed
         on completed.id = deletion.id
           and completed.layout_context_id = deletion.layout_context_id
           and completed.version = deletion.version - 1
  where deletion.design_id is not null
    and exists (select * from layout.design where design.id = deletion.design_id)
    and not deletion.draft
    and deletion.deleted
    and completed.design_asset_state = 'COMPLETED'
    and not completed.deleted
    and deletion.version = (
    select max(latest.version)
      from layout.km_post_version latest
      where latest.id = deletion.id
        and latest.layout_context_id = deletion.layout_context_id
  )
    and not exists (
    select *
      from layout.km_post live
      where live.id = deletion.id
        and live.layout_context_id = deletion.layout_context_id
  );

-- Put N back into the live table
insert into layout.km_post
  (id, track_number_id, geometry_km_post_id, km_number, layout_location, state, draft, version,
   change_user, change_time, design_id, layout_context_id, gk_location, gk_location_confirmed,
   gk_location_source, origin_design_id, design_asset_state)
select
  version_row.id, version_row.track_number_id, version_row.geometry_km_post_id,
  version_row.km_number, version_row.layout_location, version_row.state, version_row.draft,
  version_row.version, version_row.change_user, version_row.change_time, version_row.design_id,
  version_row.layout_context_id, version_row.gk_location, version_row.gk_location_confirmed,
  version_row.gk_location_source, version_row.origin_design_id, version_row.design_asset_state
  from layout.km_post_version version_row
    join restore_km_post restore using (id, layout_context_id, version);

-- Re-point publication references from N+1 to N before N+1 is deleted
update publication.km_post publication_row
set version = restore.version
  from restore_km_post restore
  where publication_row.id = restore.id
    and publication_row.layout_context_id = restore.layout_context_id
    and publication_row.version = restore.version + 1;

update publication.km_post publication_row
set base_version = restore.version
  from restore_km_post restore
  where publication_row.id = restore.id
    and publication_row.base_layout_context_id = restore.layout_context_id
    and publication_row.base_version = restore.version + 1;

-- Remove N+1 from history
delete
  from layout.km_post_version version_row
  using restore_km_post restore
  where version_row.id = restore.id
    and version_row.layout_context_id = restore.layout_context_id
    and version_row.version = restore.version + 1;

-- Clear N's expiry
update layout.km_post_version version_row
set expiry_time = null
  from restore_km_post restore
  where version_row.id = restore.id
    and version_row.layout_context_id = restore.layout_context_id
    and version_row.version = restore.version;

-- Re-enable the triggers
alter table layout.km_post
  enable trigger version_update_trigger;
alter table layout.km_post
  enable trigger version_row_trigger;

-- operational_point
-- Disable the triggers, so the restored row keeps its own version, change time and user
alter table layout.operational_point
  disable trigger version_update_trigger;
alter table layout.operational_point
  disable trigger version_row_trigger;

-- Find the chains to restore
create temporary table restore_operational_point on commit drop as
select completed.id, completed.layout_context_id, completed.version
  from layout.operational_point_version deletion
    join layout.operational_point_version completed
         on completed.id = deletion.id
           and completed.layout_context_id = deletion.layout_context_id
           and completed.version = deletion.version - 1
  where deletion.design_id is not null
    and exists (select * from layout.design where design.id = deletion.design_id)
    and not deletion.draft
    and deletion.deleted
    and completed.design_asset_state = 'COMPLETED'
    and not completed.deleted
    and deletion.version = (
    select max(latest.version)
      from layout.operational_point_version latest
      where latest.id = deletion.id
        and latest.layout_context_id = deletion.layout_context_id
  )
    and not exists (
    select *
      from layout.operational_point live
      where live.id = deletion.id
        and live.layout_context_id = deletion.layout_context_id
  );

-- Put N back into the live table
insert into layout.operational_point
  (id, draft, design_id, layout_context_id, design_asset_state, origin_design_id, name, abbreviation,
   uic_code, type, location, state, rinf_type, polygon, origin, ratko_operational_point_version,
   version, change_user, change_time, rinf_id_override)
select
  version_row.id, version_row.draft, version_row.design_id, version_row.layout_context_id,
  version_row.design_asset_state, version_row.origin_design_id, version_row.name,
  version_row.abbreviation, version_row.uic_code, version_row.type, version_row.location,
  version_row.state, version_row.rinf_type, version_row.polygon, version_row.origin,
  version_row.ratko_operational_point_version, version_row.version, version_row.change_user,
  version_row.change_time, version_row.rinf_id_override
  from layout.operational_point_version version_row
    join restore_operational_point restore using (id, layout_context_id, version);

-- Re-point publication references from N+1 to N before N+1 is deleted
update publication.operational_point publication_row
set version = restore.version
  from restore_operational_point restore
  where publication_row.id = restore.id
    and publication_row.layout_context_id = restore.layout_context_id
    and publication_row.version = restore.version + 1;

update publication.operational_point publication_row
set base_version = restore.version
  from restore_operational_point restore
  where publication_row.id = restore.id
    and publication_row.base_layout_context_id = restore.layout_context_id
    and publication_row.base_version = restore.version + 1;

-- Remove N+1 from history
delete
  from layout.operational_point_version version_row
  using restore_operational_point restore
  where version_row.id = restore.id
    and version_row.layout_context_id = restore.layout_context_id
    and version_row.version = restore.version + 1;

-- Clear N's expiry
update layout.operational_point_version version_row
set expiry_time = null
  from restore_operational_point restore
  where version_row.id = restore.id
    and version_row.layout_context_id = restore.layout_context_id
    and version_row.version = restore.version;

-- Re-enable the triggers
alter table layout.operational_point
  enable trigger version_update_trigger;
alter table layout.operational_point
  enable trigger version_row_trigger;
