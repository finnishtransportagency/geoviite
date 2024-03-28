delete from layout.operating_point;
alter table layout.operating_point
  drop column update_time;
select common.add_table_metadata('layout', 'operating_point');
comment on table layout.operating_point is 'Mostly railway stations or parts of them.';
