create table integrations.ratko_push_content
(
  publication_id int primary key references publication.publication (id),
  ratko_push_id  int not null references integrations.ratko_push (id)
);

select common.add_table_metadata('integrations', 'ratko_push_content');
comment on table integrations.ratko_push_content is 'Links most recent layout publishing to Ratko push operation.';
