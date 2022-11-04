-- As a repeatable migration, this will be re-run whenever the file changes. It must be idempotent.
create temp table new_role on commit drop as
with temp(code, name, user_group) as (
    values
      ('operator', 'Operaattori', 'geoviite_operaattori'),
      ('browser', 'Selaaja', 'geoviite_selaaja')
)
select *
  from temp;

create temp table new_privilege on commit drop as
with temp(code, name, description) as (
    values
      ('all-read', 'Lukuoikeus', 'Oikeus tarkastella kaikkia geoviitteen tietoja'),
      ('all-write', 'Kirjoitusoikeus', 'Oikeus muokata kaikkia geoviitteen tietoja')
)
select *
  from temp;

create temp table new_role_privilege on commit drop as
with temp(role_code, privilege_code) as (
    values
      ('browser', 'all-read'),
      ('operator', 'all-read'),
      ('operator', 'all-write')
)
select *
  from temp;

-- Delete any rows that are not supposed to be in the data set

delete
  from common.role_privilege
  where not exists(
      select
        from new_role_privilege
        where new_role_privilege.role_code = role_privilege.role_code
          and new_role_privilege.privilege_code = role_privilege.privilege_code
    );

delete
  from common.privilege
  where not exists(select from new_privilege where new_privilege.code = privilege.code);

delete
  from common.role
  where not exists(select from new_role where new_role.code = role.code);

-- Upsert using 'except' to avoid updating rows that are already identical
insert into common.role(code, name, user_group)
select *
  from new_role
except
select code, name, user_group
  from common.role
on conflict (code) do update set name = excluded.name, user_group = excluded.user_group;

insert into common.privilege(code, name, description)
select *
  from new_privilege
except
select code, name, description
  from common.privilege
on conflict (code) do update set name = excluded.name, description = excluded.description;

insert into common.role_privilege(role_code, privilege_code)
select *
  from new_role_privilege
except
select role_code, privilege_code
  from common.role_privilege
on conflict (role_code, privilege_code) do nothing;
