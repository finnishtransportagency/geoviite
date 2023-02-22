drop view if exists layout.segment;
create view layout.segment as
(
  select segment_version.*
  from layout.segment_version
  inner join layout.alignment
    on segment_version.alignment_id = alignment.id
      and segment_version.alignment_version = alignment.version
);
