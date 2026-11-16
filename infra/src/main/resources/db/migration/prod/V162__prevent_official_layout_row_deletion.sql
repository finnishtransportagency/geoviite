-- Official (published) layout rows are never deleted: in main they are only updated, and in designs they are marked
-- CANCELLED or COMPLETED instead. Only draft rows may be deleted.
create function layout.prevent_official_row_deletion() returns trigger
  language plpgsql as
$$
begin
  raise exception 'Deleting official layout rows is not allowed: table=%.% id=% layout_context_id=%',
    tg_table_schema, tg_table_name, old.id, old.layout_context_id;
end;
$$;

create trigger prevent_official_row_deletion
  before delete on layout.track_number
  for each row when (not old.draft) execute function layout.prevent_official_row_deletion();

create trigger prevent_official_row_deletion
  before delete on layout.location_track
  for each row when (not old.draft) execute function layout.prevent_official_row_deletion();

create trigger prevent_official_row_deletion
  before delete on layout.switch
  for each row when (not old.draft) execute function layout.prevent_official_row_deletion();

create trigger prevent_official_row_deletion
  before delete on layout.km_post
  for each row when (not old.draft) execute function layout.prevent_official_row_deletion();

create trigger prevent_official_row_deletion
  before delete on layout.operational_point
  for each row when (not old.draft) execute function layout.prevent_official_row_deletion();
