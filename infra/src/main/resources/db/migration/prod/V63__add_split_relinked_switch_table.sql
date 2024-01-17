create table publication.split_switch
(
  split_id  int not null,
  switch_id int not null,

  primary key (split_id, switch_id),

  constraint split_fkey foreign key (split_id) references publication.split (id) on delete cascade,
  constraint split_switch_fkey foreign key (switch_id) references layout.switch (id)
);

select common.add_metadata_columns('publication', 'split_switch');
select common.add_table_versioning('publication', 'split_switch');
