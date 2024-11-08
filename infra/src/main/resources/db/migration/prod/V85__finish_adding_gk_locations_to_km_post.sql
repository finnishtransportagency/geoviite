update layout.km_post
set
  gk_location_confirmed = true,
  gk_location_source =
    case
      when gk_location is null then null
      when km_post.geometry_km_post_id is not null then 'FROM_GEOMETRY'::layout.gk_location_source
      else 'FROM_LAYOUT'::layout.gk_location_source
    end
  where true;

update layout.km_post_version
set
  gk_location_confirmed = true,
  gk_location_source =
    case
      when gk_location is null then null
      when km_post_version.geometry_km_post_id is not null then 'FROM_GEOMETRY'::layout.gk_location_source
      else 'FROM_LAYOUT'::layout.gk_location_source
    end
  where true;

alter table layout.km_post
  alter column gk_location_confirmed set not null;
alter table layout.km_post_version
  alter column gk_location_confirmed set not null;

drop view if exists layout.km_post_publication_view;
drop function if exists layout.km_post_in_layout_context(layout.publication_state, int);
drop function if exists layout.km_post_in_layout_context(layout.publication_state, int, int);

alter table layout.km_post rename column location to layout_location;
alter table layout.km_post_version rename column location to layout_location;

alter table layout.km_post
  enable trigger version_update_trigger;
alter table layout.km_post
  enable trigger version_row_trigger;
