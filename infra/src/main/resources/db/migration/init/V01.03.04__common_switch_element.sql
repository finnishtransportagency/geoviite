create type common.switch_element_type as enum ('LINE', 'CURVE');

create table common.switch_element
(
  alignment_id  int                        not null references common.switch_alignment (id),
  element_index int                        not null,
  type          common.switch_element_type not null,

  start_point   postgis.geometry(point)    not null,
  end_point     postgis.geometry(point)    not null,

  curve_radius  decimal(12, 6)             null,

  primary key (alignment_id, element_index)
);

select common.add_table_metadata('common', 'switch_element');
comment on table common.switch_element is 'Switch structure element: a geometric piece of a common.switch_alignment.';
