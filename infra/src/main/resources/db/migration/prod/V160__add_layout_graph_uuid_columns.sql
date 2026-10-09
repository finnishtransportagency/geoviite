alter table layout.node
  add column uuid uuid not null unique default gen_random_uuid();

alter table layout.edge
  add column uuid uuid not null unique default gen_random_uuid();

-- Postgres cannot generate random values in a "generated always as ... stored" column, as such an expression must be
-- immutable. Triggers give the same guarantee: the database always picks the value and it can never change.
create function layout.generate_row_uuid() returns trigger
  language plpgsql as
$$
begin
  new.uuid := case when tg_op = 'INSERT' then gen_random_uuid() else old.uuid end;
  return new;
end;
$$;

create trigger node_uuid_insert
  before insert on layout.node
  for each row execute function layout.generate_row_uuid();

create trigger node_uuid_update
  before update of uuid on layout.node
  for each row execute function layout.generate_row_uuid();

create trigger edge_uuid_insert
  before insert on layout.edge
  for each row execute function layout.generate_row_uuid();

create trigger edge_uuid_update
  before update of uuid on layout.edge
  for each row execute function layout.generate_row_uuid();

alter table layout.node
  alter column uuid drop default;

alter table layout.edge
  alter column uuid drop default;
