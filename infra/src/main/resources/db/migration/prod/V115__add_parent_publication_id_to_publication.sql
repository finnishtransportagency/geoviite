alter table publication.publication
  add column parent_publication_id int references publication.publication (id);
