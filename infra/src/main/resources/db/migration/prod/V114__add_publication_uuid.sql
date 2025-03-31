alter table publication.publication
  add column publication_uuid uuid not null default gen_random_uuid();
