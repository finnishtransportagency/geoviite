alter table layout.node
  add column ext_id uuid not null unique default gen_random_uuid();

alter table layout.edge
  add column ext_id uuid not null unique default gen_random_uuid();

-- Postgres cannot generate random values in a "generated always as ... stored" column, as such an expression must be
-- immutable. Triggers give the same guarantee: the database always picks the value and it can never change.
create function layout.generate_row_ext_id() returns trigger
  language plpgsql as
$$
begin
  new.ext_id := case when tg_op = 'INSERT' then gen_random_uuid() else old.ext_id end;
  return new;
end;
$$;

create trigger node_ext_id_insert
  before insert on layout.node
  for each row execute function layout.generate_row_ext_id();

create trigger node_ext_id_update
  before update of ext_id on layout.node
  for each row execute function layout.generate_row_ext_id();

create trigger edge_ext_id_insert
  before insert on layout.edge
  for each row execute function layout.generate_row_ext_id();

create trigger edge_ext_id_update
  before update of ext_id on layout.edge
  for each row execute function layout.generate_row_ext_id();

alter table layout.node
  alter column ext_id drop default;

alter table layout.edge
  alter column ext_id drop default;
