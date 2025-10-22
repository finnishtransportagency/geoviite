create table publication.operational_point
(
  publication_id         int  not null,
  id                     int  not null,
  version                int  not null,
  layout_context_id      text not null,
  base_version           int,
  base_layout_context_id text,
  constraint publication_operational_point_pk primary key (publication_id, id),
  constraint publication_operational_point_base_version_fkey
    foreign key (id, base_layout_context_id, base_version)
      references layout.operational_point_version (id, layout_context_id, version),
  constraint publication_operational_point_id_fkey
    foreign key (id)
      references layout.operational_point_id (id),
  constraint publication_operational_point_version_fkey
    foreign key (id, layout_context_id, version)
      references layout.operational_point_version (id, layout_context_id, version),
  constraint publication_operational_point_publication_fk foreign key (publication_id) references publication.publication (id)
)
