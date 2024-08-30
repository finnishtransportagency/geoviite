update publication.publication set message = '' where message is null;
alter table publication.publication alter column message set not null;
