drop view if exists layout.track_number_publication_view;
create view layout.track_number_publication_view as
(
select
  row.id as row_id,
  coalesce(official.id, row.id) as official_id,
  case when row.draft then row.id end as draft_id,
  row.version as row_version,
  case when row.draft then official.version else row.version end as official_version,
  case when row.draft then row.version end as draft_version,
  row.external_id,
  row.number,
  row.description,
  row.state,
  row.change_user,
  row.change_time,
  row.draft,
  (case
     when row.draft = true then '{"DRAFT"}'
     when exists(select 1 from layout.track_number as d where d.draft_of_track_number_id = row.id) then '{"OFFICIAL"}'
     else '{"DRAFT","OFFICIAL"}'
   end)::varchar[] as publication_states
  from layout.track_number row
    left join layout.track_number official on row.draft_of_track_number_id = official.id
);
