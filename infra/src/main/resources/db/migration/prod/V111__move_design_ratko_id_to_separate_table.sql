create table integrations.ratko_design
(
  design_id     int primary key,
  ratko_plan_id int not null
);
insert into integrations.ratko_design (
  select id, ratko_id
    from layout.design
    where ratko_id is not null
);
alter table layout.design
  drop column ratko_id;
alter table layout.design_version
  drop column ratko_id;
