alter table publication.publication
  add column design_id int,
  add constraint publication_design_fk foreign key (design_id) references layout.design (id);
