create type layout.design_state as enum ('ACTIVE', 'DELETED', 'COMPLETED');

create table layout.design
(
  id                   int primary key generated always as identity,
  name                 text                not null,
  estimated_completion date                not null,
  design_state         layout.design_state not null
);
comment on table layout.design is 'Overlays for planned changes to the layout.';

select common.add_table_metadata('layout', 'design');

alter table layout.track_number disable trigger version_update_trigger;
alter table layout.track_number disable trigger version_row_trigger;

alter table layout.reference_line disable trigger version_update_trigger;
alter table layout.reference_line disable trigger version_row_trigger;

alter table layout.location_track disable trigger version_update_trigger;
alter table layout.location_track disable trigger version_row_trigger;

alter table layout.switch disable trigger version_update_trigger;
alter table layout.switch disable trigger version_row_trigger;

alter table layout.km_post disable trigger version_update_trigger;
alter table layout.km_post disable trigger version_row_trigger;


alter table layout.track_number
  add column design_id     integer,
  add constraint track_number_design_fk foreign key (design_id) references layout.design (id),
  add column design_row_id integer,
  add constraint track_number_design_row_fk foreign key (design_row_id) references layout.track_number (id),
  add column layout_context_id varchar generated always as
    (coalesce(design_id::varchar, 'main') || '_' || case when draft then 'draft' else 'official' end) stored;

alter table layout.track_number_version
  add column design_id         integer,
  add column design_row_id     integer,
  add column layout_context_id varchar;

update layout.track_number_version
set
  layout_context_id = (coalesce(design_id::varchar, 'main') || '_' || case when draft then 'draft' else 'official' end);

alter table layout.track_number alter column layout_context_id set not null;
alter table layout.track_number_version alter column layout_context_id set not null;


alter table layout.reference_line
  add column design_id         integer,
  add constraint reference_line_design_fk foreign key (design_id) references layout.design (id),
  add column design_row_id     integer,
  add constraint reference_line_design_row_fk foreign key (design_row_id) references layout.reference_line (id),
  add column layout_context_id varchar generated always as
                                 (coalesce(design_id::varchar, 'main') || '_' ||
                                  case when draft then 'draft' else 'official' end) stored;

alter table layout.reference_line_version
  add column design_id         integer,
  add column design_row_id     integer,
  add column layout_context_id varchar;

update layout.reference_line_version
set
  layout_context_id = (coalesce(design_id::varchar, 'main') || '_' || case when draft then 'draft' else 'official' end);

alter table layout.reference_line alter column layout_context_id set not null;
alter table layout.track_number_version alter column layout_context_id set not null;


alter table layout.location_track
  add column design_id     integer,
  add constraint location_track_design_fk foreign key (design_id) references layout.design (id),
  add column design_row_id integer,
  add constraint location_track_design_row_fk foreign key (design_row_id) references layout.location_track (id),
  add column layout_context_id varchar generated always as
    (coalesce(design_id::varchar, 'main') || '_' || case when draft then 'draft' else 'official' end) stored;

alter table layout.location_track_version
  add column design_id         integer,
  add column design_row_id     integer,
  add column layout_context_id varchar;

update layout.location_track_version
set
  layout_context_id = (coalesce(design_id::varchar, 'main') || '_' || case when draft then 'draft' else 'official' end);

alter table layout.location_track alter column layout_context_id set not null;
alter table layout.location_track_version alter column layout_context_id set not null;

alter table layout.switch
  add column design_id     integer,
  add constraint switch_design_fk foreign key (design_id) references layout.design (id),
  add column design_row_id integer,
  add constraint switch_design_row_fk foreign key (design_row_id) references layout.switch (id),
  add column layout_context_id varchar generated always as
    (coalesce(design_id::varchar, 'main') || '_' || case when draft then 'draft' else 'official' end) stored;

alter table layout.switch_version
  add column design_id     integer,
  add column design_row_id integer,
  add column layout_context_id varchar;

update layout.switch_version
set
  layout_context_id = (coalesce(design_id::varchar, 'main') || '_' || case when draft then 'draft' else 'official' end);

alter table layout.switch alter column layout_context_id set not null;
alter table layout.switch_version alter column layout_context_id set not null;


alter table layout.km_post
  add column design_id     integer,
  add constraint km_post_design_fk foreign key (design_id) references layout.design (id),
  add column design_row_id integer,
  add constraint km_post_design_row_fk foreign key (design_row_id) references layout.km_post (id),
  add column layout_context_id varchar generated always as
    (coalesce(design_id::varchar, 'main') || '_' || case when draft then 'draft' else 'official' end) stored;

alter table layout.km_post_version
  add column design_id         integer,
  add column design_row_id     integer,
  add column layout_context_id varchar;

update layout.km_post_version
set
  layout_context_id = (coalesce(design_id::varchar, 'main') || '_' || case when draft then 'draft' else 'official' end);

alter table layout.km_post alter column layout_context_id set not null;
alter table layout.km_post_version alter column layout_context_id set not null;


alter table layout.track_number enable trigger version_update_trigger;
alter table layout.track_number enable trigger version_row_trigger;

alter table layout.reference_line enable trigger version_update_trigger;
alter table layout.reference_line enable trigger version_row_trigger;

alter table layout.location_track enable trigger version_update_trigger;
alter table layout.location_track enable trigger version_row_trigger;

alter table layout.switch enable trigger version_update_trigger;
alter table layout.switch enable trigger version_row_trigger;

alter table layout.km_post enable trigger version_update_trigger;
alter table layout.km_post enable trigger version_row_trigger;


--- constraints

alter table layout.track_number
  drop constraint track_number_external_id_draft_unique,
  add constraint track_number_external_id_layout_context_unique unique(external_id, layout_context_id),
  drop constraint track_number_number_draft_unique,
  add constraint track_number_number_layout_context_unique unique(number, layout_context_id),
  drop constraint track_number_official_row_id_unique,
  add constraint track_number_official_row_id_unique unique(official_row_id, layout_context_id),
  add constraint track_number_design_row_id_unique unique(design_row_id);

alter table layout.reference_line
  drop constraint reference_line_official_row_id_unique,
  add constraint reference_line_official_row_id_unique unique(official_row_id, layout_context_id),
  drop constraint reference_line_track_number_unique,
  add constraint reference_line_track_number_unique unique(track_number_id, layout_context_id),
  add constraint reference_line_design_row_id_unique unique(design_row_id);

alter table layout.location_track
  drop constraint location_track_external_id_unique,
  add constraint location_track_external_id_unique unique(external_id, layout_context_id),
  drop constraint location_track_official_row_id_unique,
  add constraint location_track_official_row_id_unique unique(official_row_id, layout_context_id),
  add constraint location_track_design_row_id_unique unique(design_row_id),
  drop constraint location_track_unique_official_name,
  add constraint location_track_unique_official_name
    exclude(track_number_id with =, name with =, layout_context_id with =)
     where (state <> 'DELETED'::layout.location_track_state and not draft) deferrable initially deferred;

alter table layout.switch
  drop constraint switch_external_id_unique,
  add constraint switch_external_id_unique unique(external_id, layout_context_id),
  drop constraint switch_official_row_id_unique,
  add constraint switch_official_row_id_unique unique(official_row_id, layout_context_id),
  add constraint switch_design_row_id_unique unique(design_row_id),
  drop constraint switch_unique_official_name,
  add constraint switch_unique_official_name
    exclude (name with =, layout_context_id with =) where (state_category != 'NOT_EXISTING' and not draft);

alter table layout.km_post
  drop constraint km_post_official_row_id_unique,
  add constraint km_post_official_row_id_unique unique(official_row_id, layout_context_id),
  add constraint km_post_design_row_id_unique unique(design_row_id),
  drop constraint km_post_track_number_km_unique,
  add constraint km_post_track_number_km_unique unique(track_number_id, km_number, layout_context_id);
