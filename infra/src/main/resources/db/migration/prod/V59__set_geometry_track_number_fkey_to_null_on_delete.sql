alter table geometry.plan
  drop constraint plan_track_number_id_fkey;
alter table geometry.plan
  add constraint plan_track_number_id_fkey foreign key (track_number_id)
    references layout.track_number (id) on delete set null;

alter table geometry.alignment
  drop constraint alignment_track_number_id_fkey;
alter table geometry.alignment
  add constraint alignment_track_number_id_fkey foreign key (track_number_id)
    references layout.track_number (id) on delete set null;

alter table geometry.km_post
  drop constraint km_post_track_number_id_fkey;
alter table geometry.km_post
  add constraint km_post_track_number_id_fkey foreign key (track_number_id)
    references layout.track_number (id) on delete set null;
