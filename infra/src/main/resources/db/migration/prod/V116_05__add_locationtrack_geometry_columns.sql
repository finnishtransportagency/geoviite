alter table layout.location_track disable trigger version_update_trigger;
alter table layout.location_track disable trigger version_row_trigger;

-- Add these columns to location_track as alignment is going away
alter table layout.location_track_version
  add column bounding_box postgis.geometry(polygon, 3067) null,
  add column length decimal(13, 6) null,
  add column edge_count int null,
  add column segment_count int null;

alter table layout.location_track
  add column bounding_box postgis.geometry(polygon, 3067) null,
  add column length decimal(13, 6) null,
  add column edge_count int null,
  add column segment_count int null;

update layout.location_track_version ltv
set
  edge_count = (
    select count(*)
      from layout.location_track_version_edge ltve
      where ltve.location_track_id = ltv.id
        and ltve.location_track_layout_context_id = ltv.layout_context_id
        and ltve.location_track_version = ltv.version
  ) where true;

update layout.location_track_version ltv
set
  bounding_box = a.bounding_box,
  length = a.length,
  segment_count = a.segment_count
from layout.alignment_version a
where
  ltv.alignment_id = a.id and
  ltv.alignment_version = a.version;

update layout.location_track
set
  bounding_box = ltv.bounding_box,
  length = ltv.length,
  edge_count = ltv.edge_count,
  segment_count = ltv.segment_count
from layout.location_track_version ltv
where
  location_track.id = ltv.id and
  location_track.layout_context_id = ltv.layout_context_id and
  location_track.version = ltv.version;

alter table layout.location_track_version
  alter column length set not null,
  alter column edge_count set not null,
  alter column segment_count set not null;

alter table layout.location_track
  alter column length set not null,
  alter column edge_count set not null,
  alter column segment_count set not null;

-- TODO: Drop these columns instead, but keep them for now to maintain the data for comparison
alter table layout.location_track_version alter column alignment_id drop not null;
alter table layout.location_track_version alter column alignment_version drop not null;
alter table layout.location_track alter column alignment_id drop not null;
alter table layout.location_track alter column alignment_version drop not null;

alter table layout.location_track enable trigger version_row_trigger;
alter table layout.location_track enable trigger version_update_trigger;
