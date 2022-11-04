drop index if exists geometry.geometry_element_switch;
create index geometry_element_switch on geometry.element(switch_id);
