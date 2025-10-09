create table common.oid_spaces
(
  name      text not null primary key,
  oid_space text not null,
  constraint oid_spaces_oid_space_uk unique (oid_space)
);

insert into common.oid_spaces
  values
    ('Geoviite', '1.2.246.578.13');


create table common.oid_types
(
  type_name text not null primary key
);

create type common.oid_sequence_state as enum (
  'ACTIVE',
  'DEPRECATED'
  );

create table common.oid_sequences
(
  group_number integer                   not null,
  number       integer                   not null,
  service_oid  text                      not null,
  type         text                      not null references common.oid_types (type_name),
  state        common.oid_sequence_state not null,
  oid          text                      not null generated always as (service_oid || '.' || group_number || '.' || number) stored,
  seq_name     text                      not null generated always as ('oid_' || service_oid || '.' || group_number || '.' || number || '_seq') stored,
  primary key (group_number, number),
  constraint oid_sequences_service_oid_fk foreign key (service_oid) references common.oid_spaces (oid_space),
  constraint oid_sequences_type_uk unique (type)
);

create function insert_oid_sequence() returns trigger as
$$
begin
  execute format('create sequence if not exists common.%I', new.seq_name);
  return new;
end;
$$
  language plpgsql;

create trigger insert_oid_sequence_trigger
  after insert
  on common.oid_sequences
  for each row
execute function insert_oid_sequence();

create function common.generate_oid(type_in text) returns text as
$$
select service_oid || '.' || group_number || '.' || number || '.' || nextval(format('common.%I', seq_name))
  from common.oid_sequences
  where type = type_in
$$
  language sql;
