alter table geometry.plan_file
  add hash text not null generated always as (md5(content::text)) stored;

