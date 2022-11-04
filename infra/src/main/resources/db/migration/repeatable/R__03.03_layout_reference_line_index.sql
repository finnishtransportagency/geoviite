drop index if exists layout.layout_reference_line_alignment;
create index layout_reference_line_alignment on layout.reference_line(alignment_id);
