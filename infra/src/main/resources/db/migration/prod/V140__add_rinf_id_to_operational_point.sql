alter table layout.operational_point
  disable trigger version_update_trigger;
alter table layout.operational_point
  disable trigger version_row_trigger;


create sequence if not exists layout.rinf_id_seq
  start with 100000
  increment by 1;

create or replace function layout.generate_rinf_id()
  returns varchar(12)
  language sql
as
$$
select 'FI' || nextval('layout.rinf_id_seq')::text;
$$;

alter table layout.operational_point
  add column rinf_id_generated varchar(12);
alter table layout.operational_point
  add column rinf_id_override varchar(12);

alter table layout.operational_point_version
  add column rinf_id_generated varchar(12);
alter table layout.operational_point_version
  add column rinf_id_override varchar(12);

alter table layout.operational_point
  enable trigger version_update_trigger;
alter table layout.operational_point
  enable trigger version_row_trigger;

create or replace function layout.check_rinf_id_generated_uniqueness()
  returns trigger
  language plpgsql
as $$
begin
  if new.rinf_id_generated is not null then
    if exists (
      select 1 from layout.operational_point
      where rinf_id_generated = new.rinf_id_generated
        and id != new.id
    ) then
      raise exception 'rinf_id_generated % already exists for a different operational point', new.rinf_id_generated
        using errcode = 'unique_violation';
    end if;
  end if;
  return new;
end;
$$;

create trigger operational_point_rinf_id_generated_unique_check
  before insert or update on layout.operational_point
  for each row
  execute function layout.check_rinf_id_generated_uniqueness();
