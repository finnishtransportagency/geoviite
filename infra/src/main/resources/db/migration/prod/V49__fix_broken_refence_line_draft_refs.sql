delete from layout.reference_line rl
where rl.draft
  and exists (
  select 1
    from layout.track_number tn
    where tn.id = rl.track_number_id
      and tn.draft
      and tn.draft_of_track_number_id is not null
);
