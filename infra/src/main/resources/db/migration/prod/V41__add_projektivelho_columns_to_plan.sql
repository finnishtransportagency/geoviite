alter table geometry.plan
  add column projektivelho_file_metadata_id int null references projektivelho.file_metadata (id),
  add column projektivelho_assignment_oid varchar(50) null references projektivelho.assignment (oid),
  add column projektivelho_project_oid varchar(50) null references projektivelho.project (oid),
  add column projektivelho_project_group_oid varchar(50) null references projektivelho.project_group (oid),
  drop column oid
;
alter table geometry.plan_version
  add column projektivelho_file_metadata_id int null,
  add column projektivelho_assignment_oid varchar(50) null,
  add column projektivelho_project_oid varchar(50) null,
  add column projektivelho_project_group_oid varchar(50) null,
  drop column oid
;
