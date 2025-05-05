-- As a repeatable migration, this will be re-run whenever the file changes. It must be idempotent.
create temp table new_role on commit drop as
with temp(code, user_group) as (
    values
      ('operator', 'geoviite_operaattori'),
      ('browser', 'geoviite_selaaja'), -- Deprecated: remove when users are updated in AD to "authority"
      ('authority', 'geoviite_virasto'),
      ('consultant', 'geoviite_konsultti'),
      ('team', 'geoviite_tiimi'),
      ('api-public', 'geoviite_api_julkinen'),
      ('api-private', 'geoviite_api_yksityinen')
)
select *
  from temp;

create temp table new_privilege on commit drop as
with temp(code) as (
    values
      ('view-basic'),
      ('view-layout'),
      ('view-layout-draft'),
      ('edit-layout'),
      ('view-geometry'),
      ('edit-geometry-file'),
      ('download-geometry'),
      ('view-pv-documents'),
      ('view-geometry-file'),
      ('view-publication'),
      ('download-publication'),
      ('api-frame-converter'),
      ('api-geometry'),
      ('api-swagger')
)
select *
  from temp;

create temp table new_role_privilege on commit drop as
with temp(role_code, privilege_code) as (
    values
      ('operator', 'view-basic'),
      ('operator', 'view-layout'),
      ('operator', 'view-layout-draft'),
      ('operator', 'edit-layout'),
      ('operator', 'view-geometry'),
      ('operator', 'edit-geometry-file'),
      ('operator', 'download-geometry'),
      ('operator', 'view-pv-documents'),
      ('operator', 'view-geometry-file'),
      ('operator', 'view-publication'),
      ('operator', 'download-publication'),

      ('team', 'view-basic'),
      ('team', 'view-layout'),
      ('team', 'view-layout-draft'),
      ('team', 'view-geometry'),
      ('team', 'view-publication'),
      ('team', 'download-publication'),
      ('team', 'download-geometry'),
      ('team', 'view-pv-documents'),
      ('team', 'view-geometry-file'),

      ('browser', 'view-basic'),
      ('browser', 'view-layout'),
      ('browser', 'view-geometry'),
      ('browser', 'view-geometry-file'),
      ('browser', 'view-publication'),
      ('browser', 'download-publication'),

      ('authority', 'view-basic'),
      ('authority', 'view-layout'),
      ('authority', 'view-geometry'),
      ('authority', 'view-geometry-file'),
      ('authority', 'view-publication'),
      ('authority', 'download-publication'),

      ('consultant', 'view-basic'),
      ('consultant', 'view-publication'),
      ('consultant', 'download-publication'),
      ('consultant', 'view-layout'),

      ('api-public', 'api-frame-converter'),
      ('api-public', 'api-swagger'),

      ('api-private', 'api-frame-converter'),
      ('api-private', 'api-geometry'),
      ('api-private', 'api-swagger')
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
  where not exists(
    select
      from new_privilege
      where new_privilege.code = privilege.code
  );

delete
  from common.role
  where not exists(
    select
      from new_role
      where new_role.code = role.code
  );

-- Upsert using 'except' to avoid updating rows that are already identical
insert into common.role(code, user_group)
select *
  from new_role
except
select code, user_group
  from common.role
on conflict (code) do update set user_group = excluded.user_group;

insert into common.privilege(code)
select *
  from new_privilege
except
select code
  from common.privilege
on conflict (code) do nothing;

insert into common.role_privilege(role_code, privilege_code)
select *
  from new_role_privilege
except
select role_code, privilege_code
  from common.role_privilege
on conflict (role_code, privilege_code) do nothing;
