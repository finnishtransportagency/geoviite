-- As a repeatable migration, this will be re-run whenever the file changes. It must be idempotent.
create temp table new_role on commit drop as
with temp(code, name, user_group) as (
    values
      ('operator', 'Operaattori', 'geoviite_operaattori'),
      ('browser', 'Selaaja', 'geoviite_selaaja'), -- Deprecated: remove when users are updated in AD to "authority"
      ('authority', 'Virastokäyttäjä', 'geoviite_virasto'),
      ('consultant', 'Konsultti', 'geoviite_konsultti'),
      ('team', 'Kehitystiimi', 'geoviite_tiimi')
)
select *
  from temp;

create temp table new_privilege on commit drop as
with temp(code, name, description) as (
    values
      ('view-basic', 'Perustietojen lukuoikeus', 'Oikeus autentikoitua Geoviitteeseen ja lukea käyttäjä- ja ympäristötietoja'),
      ('view-layout', 'Paikannuspohjan lukuoikeus', 'Oikeus tarkastella virallista paikannuspohjaa'),
      ('view-layout-draft', 'Paikannuspohjaluonnoksen luku', 'Oikeus tarkastella paikannuspohjaa luonnostilassa'),
      ('edit-layout', 'Paikannuspohjan muokkaus', 'Oikeus muokata Geoviitteen paikannuspohjaa'),
      ('view-geometry', 'Raidegeometrioiden luku', 'Oikeus listata ja tarkastella raidegeometrioita'),
      ('edit-geometry', 'Raidegeometrioiden muokkaus', 'Oikeus luoda, poistaa ja piilottaa inframalleja ja muokata niihin liittyvää metatietoa'),
      ('download-geometry', 'Inframallien lataus', 'Oikeus ladata Geoviitteestä InfraModel-tiedostoja'),
      ('view-pv-documents', 'Projektivelhoaineistojen luku', 'Oikeus listata ja tarkastella ProjektiVelhosta ladattuja aineistoja'),
      ('view-inframodel', 'Inframallien luku', 'Oikeus listata ja tarkastella Geoviitteen InfraModel-tiedostoja'),
      ('view-publication', 'Julkaisujen luku', 'Oikeus listata ja tarkastella julkaisuja'),
      ('download-publication', 'Julkaisujen lataus', 'Oikeus ladata julkaisuja')
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
      ('operator', 'edit-geometry'),
      ('operator', 'download-geometry'),
      ('operator', 'view-pv-documents'),
      ('operator', 'view-inframodel'),
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
      ('team', 'view-inframodel'),

      ('browser', 'view-basic'),
      ('browser', 'view-layout'),
      ('browser', 'view-geometry'),
      ('browser', 'view-inframodel'),
      ('browser', 'view-publication'),
      ('browser', 'download-publication'),

      ('authority', 'view-basic'),
      ('authority', 'view-layout'),
      ('authority', 'view-geometry'),
      ('authority', 'view-inframodel'),
      ('authority', 'view-publication'),
      ('authority', 'download-publication'),

      ('consultant', 'view-basic'),
      ('consultant', 'view-publication'),
      ('consultant', 'download-publication'),
      ('consultant', 'view-layout')
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
