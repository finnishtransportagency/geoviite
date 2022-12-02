drop index if exists layout.layout_segment_version_alignment_version_index;
create index layout_segment_version_alignment_version_index
  on layout.segment_version(alignment_id, alignment_version, deleted);
