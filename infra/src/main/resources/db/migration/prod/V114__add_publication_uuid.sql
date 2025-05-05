alter table publication.publication
  add column publication_uuid uuid not null unique default gen_random_uuid();
