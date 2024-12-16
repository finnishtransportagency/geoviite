alter table layout.design
  add column ratko_id int;
alter table layout.design_version
  add column ratko_id int;

create type publication.publication_cause as enum ('MANUAL', 'LAYOUT_DESIGN_CHANGE', 'LAYOUT_DESIGN_CANCELLATION', 'MERGE_FINALIZATION', 'CALCULATED_CHANGE');

alter table publication.publication
  add column design_version integer,
  add column cause          publication.publication_cause,
  add constraint publication_design_version_consistency_check check ((design_id is null) = (design_version is null)),
  add constraint publication_design_version_fk foreign key (design_id, design_version) references layout.design_version (id, version);

update publication.publication set cause = 'MANUAL';

alter table publication.publication
  alter column cause set not null;
