insert into publication.switch_location_tracks
  (publication_id, switch_id, location_track_id, location_track_version, is_topology_switch)
select distinct
  publication.id,
  switch.switch_id,
  location_track.id,
  location_track.version,
  location_track.is_topology
  from publication.publication
    inner join publication.calculated_change_to_switch switch on switch.publication_id = publication.id
    inner join lateral (
    select location_track.id, location_track.version, draft, false as is_topology
      from layout.location_track_at(publication.publication_time) location_track
        inner join layout.segment_at(publication.publication_time) segment
                   on segment.alignment_id = location_track.alignment_id
                     and segment.alignment_version = location_track.alignment_version
      where segment.switch_id = switch.switch_id
    union all
    select id, version, draft, true as is_topology
      from layout.location_track_at(publication.publication_time)
      where topology_start_switch_id = switch.switch_id or topology_end_switch_id = switch.switch_id
    ) location_track on not location_track.draft;
