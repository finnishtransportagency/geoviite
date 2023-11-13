create table publication.location_track_geometry_change_summary
(
  publication_id    integer          not null,
  location_track_id integer          not null,
  remark_order      integer          not null,
  changed_length_m  double precision not null,
  max_distance      double precision not null,
  start_km          text             not null,
  end_km            text             not null,
  constraint location_track_geometry_change_summary_pk
    primary key (publication_id, location_track_id, remark_order),
  constraint location_track_geometry_change_summary_publication_fk
    foreign key (publication_id)
      references publication.publication (id),
  constraint location_track_geometry_change_summary_publication_lt_fk
    foreign key (publication_id, location_track_id)
      references publication.location_track (publication_id, location_track_id)
);

alter table publication.location_track
  add column geometry_change_summary_computed boolean not null default false;

update publication.location_track set geometry_change_summary_computed = true where not direct_change;
