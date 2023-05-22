create table integrations.projektivelho_document_type
(
  code varchar(50) primary key not null,
  name varchar(100)            not null
);
comment on table integrations.projektivelho_document_type is 'Localized ProjektiVelho document types (dokumenttityyppi)';
select common.add_metadata_columns('integrations', 'projektivelho_document_type');
select common.add_table_versioning('integrations', 'projektivelho_document_type');

create table integrations.projektivelho_material_state
(
  code varchar(50) primary key not null,
  name varchar(100)            not null
);
comment on table integrations.projektivelho_material_state is 'Localized ProjektiVelho material states (ainestotila)';
select common.add_metadata_columns('integrations', 'projektivelho_material_state');
select common.add_table_versioning('integrations', 'projektivelho_material_state');

create table integrations.projektivelho_material_group
(
  code varchar(50) primary key not null,
  name varchar(100)            not null
);
comment on table integrations.projektivelho_material_group is 'Localized Projektivelho material group (aineistoryhmä) options';
select common.add_metadata_columns('integrations', 'projektivelho_material_group');
select common.add_table_versioning('integrations', 'projektivelho_material_group');

create table integrations.projektivelho_material_category
(
  code varchar(50) primary key not null,
  name varchar(100)            not null
);
comment on table integrations.projektivelho_material_category is 'Localized Projektivelho material category (ainestolaji) options';
select common.add_metadata_columns('integrations', 'projektivelho_material_category');
select common.add_table_versioning('integrations', 'projektivelho_material_category');

create table integrations.projektivelho_technics_field
(
  code varchar(50) primary key not null,
  name varchar(100)            not null
);
comment on table integrations.projektivelho_technics_field is 'Localized Projektivelho technics field (tekniikka-ala) options';
select common.add_metadata_columns('integrations', 'projektivelho_technics_field');
select common.add_table_versioning('integrations', 'projektivelho_technics_field');

create table integrations.projektivelho_project_state
(
  code varchar(50) primary key not null,
  name varchar(100)            not null
);
comment on table integrations.projektivelho_project_state is 'Localized Projektivelho project state (projektin tila) options';
select common.add_metadata_columns('integrations', 'projektivelho_project_state');
select common.add_table_versioning('integrations', 'projektivelho_project_state');
