create table layout.element_listing_file
(
  id int primary key generated always as identity,
  name varchar(100) not null,
  content varchar not null
);
select common.add_table_metadata('layout', 'element_listing_file');
