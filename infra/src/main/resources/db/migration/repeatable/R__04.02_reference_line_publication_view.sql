drop view if exists layout.reference_line_publication_view;
create view layout.reference_line_publication_view as
(
select
  row.id as row_id,
  coalesce(official.id, row.id) as official_id,
  case when row.draft then row.id end as draft_id,
  row.version as row_version,
  case when row.draft then official.version else row.version end as official_version,
  case when row.draft then row.version end as draft_version,
  row.track_number_id,
  row.alignment_id,
  row.alignment_version,
  row.start_address,
  row.draft,
  row.change_user,
  row.change_time,
  alignment.bounding_box,
  alignment.length,
  alignment.segment_count,
  (case
     when row.draft = true then '{"DRAFT"}'
     when exists(select 1 from layout.reference_line as d where d.draft_of_reference_line_id = row.id)
       then '{"OFFICIAL"}'
     else '{"DRAFT","OFFICIAL"}'
   end)::varchar[] as publication_states
  from layout.reference_line row
    left join layout.reference_line official on row.draft_of_reference_line_id = official.id
    left join layout.alignment on row.alignment_id = alignment.id
    );
