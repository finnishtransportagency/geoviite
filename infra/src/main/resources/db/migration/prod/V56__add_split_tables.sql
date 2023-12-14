create type publication.split_push_state as enum ('PENDING', 'IN_PROGRESS', 'DONE', 'FAILED');

create table publication.split
(
    id                          int                             primary key generated always as identity,
    state                       publication.split_push_state    not null default 'PENDING',
    error_cause                 text                            null,
    publication_id              int                             null,
    source_location_track_id    int                             not null unique,

    constraint split_publication_fkey
        foreign key (publication_id) references publication.publication(id),

    constraint split_source_location_track_fkey
        foreign key (source_location_track_id) references layout.location_track(id)
);

create table publication.split_target_location_track
(
    split_id                    int                             not null,
    location_track_id           int                             not null,
    source_start_segment_index  int                             not null,
    source_end_segment_index    int                             not null,

    constraint split_target_location_track_unique unique (split_id, location_track_id)
);
