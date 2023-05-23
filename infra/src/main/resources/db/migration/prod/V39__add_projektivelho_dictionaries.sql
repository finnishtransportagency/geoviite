create table projektivelho.document_type
(
  code varchar(50) primary key not null,
  name varchar(100)            not null
);
comment on table projektivelho.document_type is 'Localized ProjektiVelho document types (dokumenttityyppi)';
select common.add_metadata_columns('projektivelho', 'document_type');
select common.add_table_versioning('projektivelho', 'document_type');

create table projektivelho.material_state
(
  code varchar(50) primary key not null,
  name varchar(100)            not null
);
comment on table projektivelho.material_state is 'Localized ProjektiVelho material states (ainestotila)';
select common.add_metadata_columns('projektivelho', 'material_state');
select common.add_table_versioning('projektivelho', 'material_state');

create table projektivelho.material_group
(
  code varchar(50) primary key not null,
  name varchar(100)            not null
);
comment on table projektivelho.material_group is 'Localized Projektivelho material group (aineistoryhmä) options';
select common.add_metadata_columns('projektivelho', 'material_group');
select common.add_table_versioning('projektivelho', 'material_group');

create table projektivelho.material_category
(
  code varchar(50) primary key not null,
  name varchar(100)            not null
);
comment on table projektivelho.material_category is 'Localized Projektivelho material category (ainestolaji) options';
select common.add_metadata_columns('projektivelho', 'material_category');
select common.add_table_versioning('projektivelho', 'material_category');

create table projektivelho.technics_field
(
  code varchar(50) primary key not null,
  name varchar(100)            not null
);
comment on table projektivelho.technics_field is 'Localized Projektivelho technics field (tekniikka-ala) options';
select common.add_metadata_columns('projektivelho', 'technics_field');
select common.add_table_versioning('projektivelho', 'technics_field');

create table projektivelho.project_state
(
  code varchar(50) primary key not null,
  name varchar(100)            not null
);
comment on table projektivelho.project_state is 'Localized Projektivelho project state (projektin tila) options';
select common.add_metadata_columns('projektivelho', 'project_state');
select common.add_table_versioning('projektivelho', 'project_state');
