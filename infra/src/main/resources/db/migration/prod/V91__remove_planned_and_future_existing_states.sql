alter table layout.location_track disable trigger version_row_trigger;
alter table layout.location_track disable trigger version_update_trigger;
alter table layout.km_post disable trigger version_row_trigger;
alter table layout.km_post disable trigger version_update_trigger;
alter table layout.track_number disable trigger version_row_trigger;
alter table layout.track_number disable trigger version_update_trigger;
alter table layout.switch disable trigger version_row_trigger;
alter table layout.switch disable trigger version_update_trigger;

-- Drop constraints, views and functions that reference layout.state and layout.state_category. They will be recreated
-- by repeatable migrations
alter table layout.switch drop constraint if exists switch_unique_official_name;
alter table layout.location_track drop constraint if exists location_track_unique_official_name;

drop view if exists layout.switch_change_view;
drop view if exists layout.location_track_change_view;
drop view if exists layout.km_post_change_view;
drop view if exists layout.track_number_change_view;

drop view if exists layout.switch_publication_view;
drop view if exists layout.location_track_publication_view;
drop view if exists layout.km_post_publication_view;
drop view if exists layout.track_number_publication_view;

drop function if exists layout.infer_operation_from_location_track_state_transition(old_state layout.location_track_state, new_state layout.location_track_state);
drop function if exists layout.infer_operation_from_state_transition(old_state layout.state, new_state layout.state);
drop function if exists layout.infer_operation_from_state_category_transition(old_state layout.state_category, new_state layout.state_category);

drop function if exists layout.track_number_in_layout_context(publication_state_in layout.publication_state, design_id_in integer, official_id_in integer);
drop function if exists layout.km_post_in_layout_context(publication_state_in layout.publication_state, design_id_in integer, official_id_in integer);
drop function if exists layout.location_track_in_layout_context(publication_state_in layout.publication_state, design_id_in integer, official_id_in integer);
drop function if exists layout.switch_in_layout_context(publication_state_in layout.publication_state, design_id_in integer, official_id_in integer);

-- Create new enums
alter type layout.state rename to state_old;
create type layout.state as enum ('IN_USE', 'NOT_IN_USE', 'DELETED');

alter type layout.location_track_state rename to location_track_state_old;
create type layout.location_track_state as enum ('IN_USE', 'NOT_IN_USE', 'DELETED', 'BUILT');

alter type layout.state_category rename to state_category_old;
create type layout.state_category as enum ('EXISTING', 'NOT_EXISTING');

-- Migrate existing data to new enums
alter table layout.switch
  alter column state_category type layout.state_category
    using layout.switch.state_category::text::layout.state_category;
alter table layout.switch_version
  alter column state_category type layout.state_category
    using layout.switch_version.state_category::text::layout.state_category;

alter table layout.location_track
  alter column state type layout.location_track_state
    using layout.location_track.state::text::layout.location_track_state;
alter table layout.location_track_version
  alter column state type layout.location_track_state
    using layout.location_track_version.state::text::layout.location_track_state;

alter table layout.km_post
  alter column state type layout.state
    using layout.km_post.state::text::layout.state;
alter table layout.km_post_version
  alter column state type layout.state
    using layout.km_post_version.state::text::layout.state;

alter table layout.track_number
  alter column state type layout.state
    using layout.track_number.state::text::layout.state;
alter table layout.track_number_version
  alter column state type layout.state
    using layout.track_number_version.state::text::layout.state;

-- Delete old enums
drop type layout.state_old;
drop type layout.location_track_state_old;
drop type layout.state_category_old;

-- Recreate constraints
alter table layout.switch
  add constraint switch_unique_official_name
    exclude (name with =, layout_context_id with =) where (state_category != 'NOT_EXISTING' and not draft);

alter table layout.location_track
  add constraint location_track_unique_official_name
    exclude(track_number_id with =, name with =, layout_context_id with =)
     where (state <> 'DELETED'::layout.location_track_state and not draft) deferrable initially deferred;

-- Re-enable version triggers
alter table layout.location_track enable trigger version_row_trigger;
alter table layout.location_track enable trigger version_update_trigger;
alter table layout.km_post enable trigger version_row_trigger;
alter table layout.km_post enable trigger version_update_trigger;
alter table layout.track_number enable trigger version_row_trigger;
alter table layout.track_number enable trigger version_update_trigger;
alter table layout.switch enable trigger version_row_trigger;
alter table layout.switch enable trigger version_update_trigger;
