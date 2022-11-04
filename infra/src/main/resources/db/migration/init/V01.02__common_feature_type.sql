create table common.feature_type
(
  code        varchar(3) primary key not null,
  description varchar(100)           not null
);

select common.add_table_metadata('common', 'feature_type');
comment on table common.feature_type is
  'Alignment feature type: implemented from https://buildingsmart.fi/infrabim/infrabim-nimikkeisto/';
