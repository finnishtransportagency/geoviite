alter table geometry.plan
  add column bounding_polygon_simple postgis.geometry(polygon, 3067) generated always as (
    postgis.st_simplifypreservetopology(
        postgis.st_buffer(bounding_polygon, 500, 'side=left'),
        150)
    ) stored,

  add constraint plan_track_number_id_fkey foreign key (track_number_id) references layout.track_number (id),
  add constraint plan_plan_application_id_fkey foreign key (plan_application_id) references geometry.plan_application (id),
  add constraint plan_plan_author_id_fkey foreign key (plan_author_id) references geometry.plan_author (id),
  add constraint plan_plan_project_id_fkey foreign key (plan_project_id) references geometry.plan_project (id),
  add constraint plan_linked_as_plan_id_fkey foreign key (linked_as_plan_id) references geometry.plan (id),
  add constraint plan_srid_fkey foreign key (srid) references postgis.spatial_ref_sys (srid);

select common.add_table_versioning('geometry', 'plan');

alter table geometry.plan_file
  add column change_time timestamptz not null default now(),
  add constraint plan_file_plan_id_fkey foreign key (plan_id) references geometry.plan (id) on delete set null,
  add constraint plan_file_plan_id_unique unique (plan_id);

alter table geometry.km_post
  add column change_time timestamptz not null default now(),
  add constraint km_post_track_number_id_fkey foreign key (track_number_id) references layout.track_number (id),
  add constraint km_post_plan_id_fkey foreign key (plan_id) references geometry.plan (id);

alter table geometry.switch
  add column change_time timestamptz not null default now(),
  add constraint switch_plan_id_fkey foreign key (plan_id) references geometry.plan (id),
  add constraint switch_switch_structure_id_fkey foreign key (switch_structure_id) references common.switch_structure (id);

alter table geometry.switch_joint
  add column change_time timestamptz not null default now(),
  add constraint switch_joint_switch_id_fkey foreign key (switch_id) references geometry.switch (id) on delete cascade;

alter table geometry.alignment
  add column change_time timestamptz not null default now(),
  add constraint alignment_plan_id_fkey foreign key (plan_id) references geometry.plan (id),
  add constraint alignment_track_number_id_fkey foreign key (track_number_id) references layout.track_number (id),
  add constraint alignment_feature_type_code_fkey foreign key (feature_type_code) references common.feature_type (code);

alter table geometry.element
  add column change_time timestamptz not null default now(),
  add constraint element_alignment_id_fkey foreign key (alignment_id) references geometry.alignment (id) on delete cascade,
  add constraint element_switch_id_fkey foreign key (switch_id) references geometry.switch (id),
  add constraint element_switch_start_joint_fkey foreign key (switch_id, switch_start_joint_number) references geometry.switch_joint (switch_id, number),
  add constraint element_switch_end_joint_fkey foreign key (switch_id, switch_end_joint_number) references geometry.switch_joint (switch_id, number);

alter table geometry.vertical_intersection
  add column change_time timestamptz not null default now(),
  add constraint vertical_intersection_alignment_fkey foreign key (alignment_id) references geometry.alignment (id) on delete cascade;

alter table geometry.cant_point
  add column change_time timestamptz not null default now(),
  add constraint cant_point_alignment_fkey foreign key (alignment_id) references geometry.alignment (id) on delete cascade;
