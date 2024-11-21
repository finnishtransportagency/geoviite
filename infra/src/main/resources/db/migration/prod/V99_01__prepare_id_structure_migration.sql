create function check_no_design_publications()
  returns void
  language plpgsql as
$$
begin
  if exists (
    select *
      from publication.publication
      where design_id is not null
  )
  then
    raise 'Can''t do ID structure migration with design publications existing';
  end if;
end;
$$;

select check_no_design_publications();
drop function check_no_design_publications();

create function layout.layout_context_id(design_id int, draft boolean) returns text
  language sql
  immutable
as
$$
select format('%s_%s', coalesce(design_id::text, 'main'), case when draft then 'draft' else 'official' end);
$$;
