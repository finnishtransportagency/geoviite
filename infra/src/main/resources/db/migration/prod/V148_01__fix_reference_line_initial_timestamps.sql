-- Fix historical data issue: align reference_line initial version timestamps with track_number
--
-- Background: Some historical track_number/reference_line pairs were created in the same CSV import,
-- but due to timing, reference_line change_time is slightly later than track_number change_time.
-- Also, some initial ones lacked reference_lines entirely, which was later fixed via a migration,
-- with basically the same result.
-- This causes issues in version merging as we expect synchronized timestamps.
-- Since both are "initial data" state, we align them to the earlier (track_number) timestamp.

-- Disable triggers
alter table layout.reference_line
  disable trigger version_update_trigger,
  disable trigger version_row_trigger;

-- Update reference_line_version timestamps to match track_number timestamps
-- Only updates rows that match the know criteria to avoid unintended changes

update layout.reference_line_version rlv
set change_time = tnv.change_time
  from layout.track_number_version tnv
  where rlv.track_number_id = tnv.id
    and rlv.layout_context_id = 'main_official'
    and tnv.layout_context_id = 'main_official'
    and tnv.change_user = 'CSV_IMPORT'
    and rlv.change_user in ('CSV_IMPORT', 'INIT')
    and rlv.change_time > tnv.change_time
    and (tnv.expiry_time is null or rlv.change_time < tnv.expiry_time)
    and rlv.version = 1
    and tnv.version = 1;

-- Update reference_line main table to match (for current official state)
update layout.reference_line rl
set change_time = rlv.change_time
  from layout.reference_line_version rlv
  where rl.id = rlv.id
    and rl.layout_context_id = rlv.layout_context_id
    and rl.version = rlv.version
    and rl.change_time > rlv.change_time;

-- Re-enable triggers
alter table layout.reference_line
  enable trigger version_update_trigger,
  enable trigger version_row_trigger;

-- Verify all official versions now have matching timestamps
do
$$
  begin
    if exists(
      select 1
        from layout.track_number_version tnv
          left join layout.reference_line_version rlv
                    on rlv.track_number_id = tnv.id
                      and rlv.layout_context_id = 'main_official'
                      and rlv.change_time = tnv.change_time
                      and (tnv.expiry_time is null or tnv.expiry_time > rlv.change_time)
        where tnv.layout_context_id = 'main_official'
          and tnv.change_user = 'CSV_IMPORT'
          and tnv.version = 1
          and rlv.id is null
    ) then
      raise exception 'Post-fix validation failed: official track number versions still have missing reference lines.';
    end if;
  end
$$;
