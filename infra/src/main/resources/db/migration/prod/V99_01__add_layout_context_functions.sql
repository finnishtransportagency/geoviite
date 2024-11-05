create function layout.layout_context_id(design_id int, draft boolean) returns text
  language sql
  immutable
as
$$
select format('%s_%s', coalesce(design_id::text, 'main'), case when draft then 'draft' else 'official' end);
$$;
