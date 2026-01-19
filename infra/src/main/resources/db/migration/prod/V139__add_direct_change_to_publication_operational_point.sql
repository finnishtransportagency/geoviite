alter table publication.operational_point
  add column direct_change boolean not null default true;
