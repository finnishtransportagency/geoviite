-- increment to force a rerun: 1

drop view if exists layout.location_track_publication_view;
create view layout.location_track_publication_view as
(
select
  row.id as row_id,
  coalesce(official.id, row.id) as official_id,
  case when row.draft then row.id end as draft_id,
  row.version as row_version,
  case when row.draft then official.version else row.version end as official_version,
  case when row.draft then row.version end as draft_version,
  row.alignment_id,
  row.alignment_version,
  row.track_number_id,
  row.external_id,
  row.name,
  row.description_base,
  row.description_suffix,
  row.type,
  row.state,
  row.draft,
  row.duplicate_of_location_track_id,
  row.topological_connectivity,
  row.topology_start_switch_id,
  row.topology_start_switch_joint_number,
  row.topology_end_switch_id,
  row.topology_end_switch_joint_number,
  row.change_user,
  row.change_time,
  alignment.bounding_box,
  alignment.length,
  alignment.segment_count,
  (case
     when row.draft = true then '{"DRAFT"}'
     when exists(select 1 from layout.location_track as d where d.official_row_id = row.id)
       then '{"OFFICIAL"}'
     else '{"DRAFT","OFFICIAL"}'
   end)::varchar[] as publication_states
  from layout.location_track row
    left join layout.location_track official on row.official_row_id = official.id
    left join layout.alignment on row.alignment_id = alignment.id
    );
