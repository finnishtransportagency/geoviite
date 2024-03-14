drop view if exists layout.switch_publication_view;
create view layout.switch_publication_view as
(
select
  row.id as row_id,
  coalesce(official.id, row.id) as official_id,
  case when row.draft then row.id end as draft_id,
  row.version as row_version,
  case when row.draft then official.version else row.version end as official_version,
  case when row.draft then row.version end as draft_version,
  row.external_id,
  row.geometry_switch_id,
  row.name,
  row.switch_structure_id,
  row.state_category,
  row.trap_point,
  row.owner_id,
  row.change_user,
  row.change_time,
  row.source,
  row.draft,
  (case
     when row.draft = true then '{"DRAFT"}'
     when exists(select 1 from layout.switch as d where d.official_row_id = row.id) then '{"OFFICIAL"}'
     else '{"DRAFT","OFFICIAL"}'
   end)::varchar[] as publication_states
  from layout.switch row
    left join layout.switch official on row.official_row_id = official.id
);
