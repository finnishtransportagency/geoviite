alter table geometry.plan_file
  add hash text;

alter table geometry.plan_file
  add hash_testi varchar(200);


update geometry.plan_file set hash_testi = 'olen jännittävä arvo' where plan_file.hash_testi is null;

update geometry.plan_file set hash =md5(cast((geometry.plan_file.content) as text));

