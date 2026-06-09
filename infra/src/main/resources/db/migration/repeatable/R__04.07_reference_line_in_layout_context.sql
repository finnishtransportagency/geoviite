-- No longer relevant: layout.reference_line table was merged into layout.track_number in V148_03
drop function if exists layout.reference_line_in_layout_context(layout.publication_state, int);
drop function if exists layout.reference_line_in_layout_context(layout.publication_state, int, int);
drop function if exists layout.reference_line_is_in_layout_context(layout.publication_state, int, layout.reference_line);
