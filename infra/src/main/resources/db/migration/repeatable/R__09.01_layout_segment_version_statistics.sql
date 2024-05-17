drop statistics if exists layout.segment_version_alignment_stats;
create statistics layout.segment_version_alignment_stats on alignment_id, alignment_version from layout.segment_version;
alter statistics layout.segment_version_alignment_stats set statistics 1000;
analyze layout.segment_version;
