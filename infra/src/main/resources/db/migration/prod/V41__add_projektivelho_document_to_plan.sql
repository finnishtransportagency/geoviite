-- The OID column is unused and will be dropped. As a sanity-check, ensure that it really is unused
do $$
  begin
    if exists(select 1 from geometry.plan_version where oid is not null) then
      raise exception 'OID column should be dropped, but it has data!';
    end if;
  end;
$$;

alter table geometry.plan
  add column projektivelho_file_metadata_id int null references projektivelho.file_metadata (id),
  drop column oid;
alter table geometry.plan_version
  add column projektivelho_file_metadata_id int null,
  drop column oid;
