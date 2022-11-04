drop view if exists layout.km_post_publication_view;
create view layout.km_post_publication_view as
(
select
  row.id as row_id,
  coalesce(official.id, row.id) as official_id,
  case when row.draft then row.id end as draft_id,
  row.version as row_version,
  case when row.draft then official.version else row.version end as official_version,
  case when row.draft then row.version end as draft_version,
  row.track_number_id,
  row.geometry_km_post_id,
  row.km_number,
  row.location,
  row.state,
  row.change_user,
  row.change_time,
  (case
     when row.draft = true then '{"DRAFT"}'
     when exists(select 1 from layout.km_post as d where d.draft_of_km_post_id = row.id) then '{"OFFICIAL"}'
     else '{"DRAFT","OFFICIAL"}'
   end)::varchar[] as publication_states
  from layout.km_post row
    left join layout.km_post official on row.draft_of_km_post_id = official.id
);
