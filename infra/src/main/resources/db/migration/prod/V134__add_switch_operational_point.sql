alter table layout.switch
  add column operational_point_id int,
  add constraint switch_operational_point_fk foreign key (operational_point_id) references layout.operational_point_id (id);

create index switch_operational_point_ix on layout.switch (operational_point_id);

alter table layout.switch_version
  add column operational_point_id int;
