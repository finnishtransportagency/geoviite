alter table geometry.plan_file
  add hash text unique generated always as (md5(content::text)) stored;

