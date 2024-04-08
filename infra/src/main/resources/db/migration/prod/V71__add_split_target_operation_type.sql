create type common.split_target_operaton as enum ('CREATE', 'REPLACE', 'TRANSFER');

alter table publication.split_target_location_track disable trigger version_update_trigger;
alter table publication.split_target_location_track disable trigger version_row_trigger;

alter table publication.split_target_location_track_version
  add column operation common.split_target_operaton null;
alter table publication.split_target_location_track
  add column operation common.split_target_operaton null;

with change as (
  select
    split.id,
    target.location_track_id,
    case when change.old_state is null then 'CREATE' else 'REPLACE' end as operation
    from publication.split_target_location_track target
      left join publication.split on target.split_id = split.id
      left join publication.publication on split.publication_id = publication.id
      left join publication.location_track plt
                on publication.id = plt.publication_id and target.location_track_id = plt.location_track_id
      inner join layout.location_track_change_view change
                 on change.id = plt.location_track_id and change.version = plt.location_track_version
)
update publication.split_target_location_track_version target
  set operation = change.operation
  from change
  where target.split_id = change.id and target.location_track_id = change.location_track_id;
;
update publication.split_target_location_track target
set operation = target_version.operation
from publication.split_target_location_track_version target_version
where target.split_id = target_version.split_id
  and target.location_track_id = target_version.location_track_id
  and target_version.version = 1; -- the operation should not change after the first version

alter table publication.split_target_location_track_version alter column operation set not null;
alter table publication.split_target_location_track alter column operation set not null;

alter table publication.split_target_location_track enable trigger version_row_trigger;
alter table publication.split_target_location_track enable trigger version_update_trigger;
