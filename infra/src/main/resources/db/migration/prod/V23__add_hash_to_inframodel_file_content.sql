alter table geometry.plan_file
  add hash text generated always as (md5(content::text)) stored;
