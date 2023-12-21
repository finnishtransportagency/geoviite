create type publication.split_push_state as enum ('PENDING', 'IN_PROGRESS', 'DONE', 'FAILED');

create table publication.split
(
    id                          int                             primary key generated always as identity,
    source_location_track_id    int                             not null,
    state                       publication.split_push_state    not null default 'PENDING',
    error_cause                 text                            null default null,
    publication_id              int                             null default null,

    constraint split_publication_fkey
        foreign key (publication_id) references publication.publication(id),

    constraint split_source_location_track_fkey
        foreign key (source_location_track_id) references layout.location_track(id)
);
