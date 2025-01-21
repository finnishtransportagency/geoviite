-- km post versions

-- temporary drop to allow version history to be rewritten
alter table publication.km_post
  drop constraint publication_km_post_km_post_version_fk;

alter table layout.km_post_version
  drop constraint km_post_version_pkey;

alter table publication.km_post
  add column layout_context_id text;
update publication.km_post
set layout_context_id = 'main_official';
alter table publication.km_post alter column layout_context_id set not null;

create temporary table km_post_version_change on commit drop as
  (
    select
      layout_context_id,
      id,
      version as old_version,
          row_number() over (partition by layout_context_id, official_id order by change_time, version) as new_version
      from layout.km_post_version
  );

update publication.km_post
set km_post_version = km_post_version_change.new_version
  from km_post_version_change
  where km_post.layout_context_id = km_post_version_change.layout_context_id
    and km_post.km_post_id = km_post_version_change.id
    and km_post.km_post_version = km_post_version_change.old_version;

update layout.km_post_version
set version = km_post_version_change.new_version, id = official_id
  from km_post_version_change
  where km_post_version.layout_context_id = km_post_version_change.layout_context_id
    and km_post_version.id = km_post_version_change.id
    and km_post_version.version = km_post_version_change.old_version;

alter table layout.km_post_version
  add constraint km_post_version_pkey primary key (id, layout_context_id, version);

alter table publication.km_post
  add constraint publication_km_post_km_post_version_fk
    foreign key (km_post_id, layout_context_id, km_post_version)
      references layout.km_post_version (id, layout_context_id, version);

alter table layout.km_post_version
  add column origin_design_id int;
update layout.km_post_version
set origin_design_id = design_row.design_id
  from layout.km_post_version design_row
  where design_row.id = km_post_version.design_row_id
    and not exists (
    select *
      from layout.km_post_version future_design_row
      where future_design_row.id = design_row.id
        and future_design_row.layout_context_id = design_row.layout_context_id
        and future_design_row.version > design_row.version
  );

alter table layout.km_post_version
  drop column official_id;
alter table layout.km_post_version
  drop column design_row_id;
alter table layout.km_post_version
  drop column official_row_id;
