alter table geometry.plan
  drop constraint plan_track_number_id_fkey;
alter table geometry.plan
  add constraint plan_track_number_id_fkey foreign key (track_number_id)
    references layout.track_number (id) on delete set null;

-- Remove these columns entirely, since they currently only carry duplicate data from the plan track_number
alter table geometry.alignment
  drop column track_number_id;
alter table geometry.km_post
  drop column track_number_id;
