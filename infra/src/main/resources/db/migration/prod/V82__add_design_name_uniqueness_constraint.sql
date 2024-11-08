alter table layout.design add constraint layout_design_unique_name exclude (lower(name) with = ) where (design_state != 'DELETED');
