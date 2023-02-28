alter table publication.calculated_change_to_switch
  add switch_version int null,
  add constraint calculated_change_to_switch_version_fk foreign key (switch_id, switch_version) references layout.switch_version (id, version);

update publication.calculated_change_to_switch
set switch_version = switch.version
  from publication.publication
    inner join layout.switch_at(publication_time) switch on true
  where publication.id = calculated_change_to_switch.publication_id
    and switch.id = calculated_change_to_switch.switch_id;

alter table publication.calculated_change_to_switch
  alter column switch_version set not null;
