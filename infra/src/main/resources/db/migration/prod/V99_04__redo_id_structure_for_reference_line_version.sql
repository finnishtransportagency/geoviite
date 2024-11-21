-- reference line versions

-- temporary drop to allow version history to be rewritten
alter table publication.reference_line
  drop constraint publication_reference_line_reference_line_version_fk;

alter table layout.reference_line_version
  drop constraint reference_line_version_pkey,
  add constraint reference_line_version_pkey primary key (id, layout_context_id, version);

alter table publication.reference_line
  add column layout_context_id text;
update publication.reference_line
set layout_context_id = 'main_official';

alter table publication.reference_line alter column layout_context_id set not null;

create temporary table reference_line_version_change on commit drop as
  (
    select
      layout_context_id,
      id,
      version as old_version,
          row_number() over (partition by layout_context_id, official_id order by change_time, version) as new_version
      from layout.reference_line_version
  );

update publication.reference_line
set reference_line_version = reference_line_version_change.new_version
  from reference_line_version_change
  where reference_line.layout_context_id = reference_line_version_change.layout_context_id
    and reference_line.reference_line_id = reference_line_version_change.id
    and reference_line.reference_line_version = reference_line_version_change.old_version;

update layout.reference_line_version
set version = reference_line_version_change.new_version, id = official_id
  from reference_line_version_change
  where reference_line_version.layout_context_id = reference_line_version_change.layout_context_id
    and reference_line_version.id = reference_line_version_change.id
    and reference_line_version.version = reference_line_version_change.old_version;

alter table publication.reference_line
  add constraint publication_reference_line_reference_line_version_fk
    foreign key (reference_line_id, layout_context_id, reference_line_version)
      references layout.reference_line_version (id, layout_context_id, version);

alter table layout.reference_line_version
  add column origin_design_id int;
update layout.reference_line_version
set origin_design_id = design_row.design_id
  from layout.reference_line_version design_row
  where design_row.id = reference_line_version.design_row_id
    and not exists (
    select *
      from layout.reference_line_version future_design_row
      where future_design_row.id = design_row.id
        and future_design_row.layout_context_id = design_row.layout_context_id
        and future_design_row.version > design_row.version
  );

alter table layout.reference_line_version
  drop column official_id;
alter table layout.reference_line_version
  drop column design_row_id;
alter table layout.reference_line_version
  drop column official_row_id;
