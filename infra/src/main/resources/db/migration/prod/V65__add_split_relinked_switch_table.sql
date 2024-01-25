create table publication.split_relinked_switch
(
  split_id  int not null,
  switch_id int not null,

  primary key (split_id, switch_id),

  constraint split_fkey foreign key (split_id) references publication.split (id) on delete cascade,
  constraint split_switch_fkey foreign key (switch_id) references layout.switch (id)
);

comment on table publication.split_relinked_switch is 'Switches that were re-linked during a location track split';

select common.add_metadata_columns('publication', 'split_relinked_switch');
select common.add_table_versioning('publication', 'split_relinked_switch');
