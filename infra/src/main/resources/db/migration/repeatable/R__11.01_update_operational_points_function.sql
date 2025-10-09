drop function if exists integrations.update_operational_points_from_ratko();
drop function if exists integrations.operational_point_content_differs(layout.operational_point,
                                                                       integrations.full_ratko_operational_point);
drop type if exists integrations.full_ratko_operational_point;

create type integrations.full_ratko_operational_point as
(
  -- id column comes from the join in all_ratko_rows below
  id              int,
  -- everything else comes from ratko_operational_point and must match its definition (though can be a broader type)
  external_id     text,
  name            text,
  abbreviation    text,
  uic_code        text,
  type            layout.operational_point_type,
  location        postgis.geometry
);
comment on type integrations.full_ratko_operational_point is 'Internal type used in importing operational points from Ratko.';

create function integrations.operational_point_content_differs(a layout.operational_point,
                                                               b integrations.full_ratko_operational_point) returns boolean as
$$
select a.name != b.name or
       a.abbreviation is distinct from b.abbreviation or
       a.uic_code is distinct from b.uic_code or
       a.type != b.type or
       a.location::text != b.location::text;
$$ language sql;

create function integrations.update_operational_points_from_ratko() returns void as
$$
with
  -- (external_id)
  new_ext_ids as (
    select external_id
      from integrations.ratko_operational_point
      where not exists (
        select *
          from layout.operational_point_external_id
          where operational_point_external_id.external_id = ratko_operational_point.external_id
      )
  ),
  -- (id)
  new_ids as (insert into layout.operational_point_id (
    select
      from new_ext_ids
  ) returning id
  ),
  -- (external_id, id); totally arbitrary assignment, which is fine as this is the first time we're assigning Geoviite
  -- IDs to these OIDs
  id_assignments as (
    select id, external_id
      from (
        select external_id, row_number() over () as rownum
          from new_ext_ids
      ) ir
        join (
        select id, row_number() over () as rownum
          from new_ids
      ) er using (rownum)
  ),
  -- void; only side effects used
  added_ext_ids as (insert into layout.operational_point_external_id (id, layout_context_id, design_id, external_id)
    (
      select id, 'main_official', null, external_id
        from id_assignments
    )
  ),
  -- (id, external_id, name, abbreviation...)
  all_ratko_rows as (
    -- hack for compatibility with older production database dumps: Null out empty UIC codes
    select id, external_id, name, abbreviation, case when uic_code != '' then uic_code end as uic_code, type, location
      from integrations.ratko_operational_point
        join (
        (
          select id, external_id
            from layout.operational_point_external_id
        )
        union all
        (
          select id, external_id
            from id_assignments
        )
      ) ids using (external_id)
  ),
  -- (id, external_id, name, abbreviation...); the rows that we're going to merge into operational_point.
  -- Concurrent updates may of course mean that operational_point has changed by the time we write into it; but
  -- that's fine, as the actual write operation is just a safe upsert into the draft state
  to_update as (
    select *
      from all_ratko_rows ratko_row
      where not exists (
        select *
          from layout.operational_point up_to_date_latest
          where up_to_date_latest.id = ratko_row.id
            and not integrations.operational_point_content_differs(up_to_date_latest, ratko_row)
            and (up_to_date_latest.layout_context_id = 'main_draft'
              or (up_to_date_latest.layout_context_id = 'main_official'
                and not exists (
                  select *
                    from layout.operational_point up_to_date_draft_of_official
                    where up_to_date_draft_of_official.id = ratko_row.id
                      and up_to_date_draft_of_official.layout_context_id = 'main_draft'
                      and not integrations.operational_point_content_differs(up_to_date_draft_of_official, ratko_row)
                ))
            )
      )
  ),
  -- void; just doing insertions. Add drafts to set the state of any external operating point to 'deleted',
  -- if it went away from the integration table, and there isn't already a drafted or official deletion.
  deletions as (
    insert into layout.operational_point
      (id, draft, design_id, layout_context_id, design_asset_state, origin_design_id, origin,
       name, abbreviation, uic_code, type, location, state, rinf_type_code)
      (
        select
          id, true, null, 'main_draft', design_asset_state, origin_design_id, 'RATKO',
          name, abbreviation, uic_code, type, location, 'DELETED', rinf_type_code
          from layout.operational_point
          where not exists (
            select *
              from all_ratko_rows ratko_row
              where operational_point.id = ratko_row.id
          )
            and origin = 'RATKO'
            and state != 'DELETED'
            and not (not draft and exists (
            select *
              from layout.operational_point drafted_deletion
              where drafted_deletion.id = operational_point.id and drafted_deletion.layout_context_id = 'main_draft'
          ))
      ) on conflict (id, layout_context_id) do update set state = 'DELETED' where operational_point.state != 'DELETED'

  )
insert
  into layout.operational_point
    (id, draft, design_id, layout_context_id, design_asset_state, origin_design_id, origin,
     name, abbreviation, uic_code, type, location, state,
     rinf_type_code, polygon)
    (
      select
        id, true, null, 'main_draft', null, null, 'RATKO',
        name, abbreviation, uic_code, type, location, 'IN_USE',
        existing_point.rinf_type_code, existing_point.polygon
        from to_update
          left join lateral
            (select rinf_type_code, polygon
             from layout.operational_point existing_point
             where existing_point.id = to_update.id
               and existing_point.design_id is null
             order by draft desc
             limit 1) existing_point on (true)
    )
on conflict (id, layout_context_id) do update set
  name = excluded.name,
  abbreviation = excluded.abbreviation,
  uic_code = excluded.uic_code,
  location = excluded.location;
$$
  language sql;
