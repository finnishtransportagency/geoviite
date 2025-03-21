-- km posts

alter table layout.km_post
  disable trigger version_update_trigger;
alter table layout.km_post
  disable trigger version_row_trigger;

create table layout.km_post_id
(
  id int not null primary key generated by default as identity
);
insert into layout.km_post_id (
  select distinct id
    from layout.km_post_version
);
select
  from (
    select
      nextval('layout.km_post_id_id_seq'),
      generate_series(1, (
        select max(id)
          from layout.km_post_id
      ))
  ) forward_ids;

alter table layout.km_post
  alter column layout_context_id drop expression, -- turn generated column into an ordinary column
  add constraint layout_context_id_check check (layout.layout_context_id(design_id, draft) = layout_context_id);

alter table layout.km_post
  drop constraint km_post_design_row_fk,
  drop constraint km_post_draft_of_km_post_id_fkey,
  drop constraint km_post_official_id_fkey;

alter table publication.km_post
  drop constraint publication_km_post_km_post_fk,
  add constraint publication_km_post_km_post_fk
    foreign key (km_post_id) references layout.km_post_id (id);

alter table layout.km_post
  alter column id drop identity;
alter table layout.km_post
  drop constraint km_post_pkey;

update layout.km_post
set id = official_id, version = last_version.version
  from layout.km_post_version last_version
  where km_post.official_id = last_version.id
    and km_post.layout_context_id = last_version.layout_context_id
    and not exists (
    select *
      from layout.km_post_version future_version
      where future_version.id = last_version.id
        and future_version.layout_context_id = last_version.layout_context_id
        and future_version.version > last_version.version
  );

alter table layout.km_post
  add constraint layout_km_post_pkey primary key (id, layout_context_id),
  add constraint layout_km_post_id_fkey foreign key (id) references layout.km_post_id (id);

alter table layout.km_post
  add column origin_design_id int;
update layout.km_post
set origin_design_id = design_row.design_id
  from layout.km_post design_row
  where design_row.id = km_post.design_row_id;

alter table layout.km_post
  drop column official_id;
alter table layout.km_post
  drop column design_row_id;
alter table layout.km_post
  drop column official_row_id;

alter table layout.km_post
  enable trigger version_update_trigger;
alter table layout.km_post
  enable trigger version_row_trigger;

drop function layout.get_km_post_version(int);
select common.create_version_fetch_function('layout', 'km_post');
