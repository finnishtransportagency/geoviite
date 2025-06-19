-- Common
select common.add_version_expiry_times('common', 'role');
select common.add_version_expiry_times('common', 'privilege');
select common.add_version_expiry_times('common', 'role_privilege');
select common.add_version_expiry_times('common', 'coordinate_system');
select common.add_version_expiry_times('common', 'feature_type');
select common.add_version_expiry_times('common', 'inframodel_switch_type_name_alias');
select common.add_version_expiry_times('common', 'location_track_owner');
select common.add_version_expiry_times('common', 'switch_owner');
select common.add_version_expiry_times('common', 'switch_structure');

-- Geometry
select common.add_version_expiry_times('geometry', 'plan');
select common.add_version_expiry_times('geometry', 'plan_application');
select common.add_version_expiry_times('geometry', 'plan_author');
select common.add_version_expiry_times('geometry', 'plan_project');

-- Layout
select common.add_version_expiry_times('layout', 'design');
select common.add_version_expiry_times('layout', 'alignment');
select common.add_version_expiry_times('layout', 'track_number');
select common.add_version_expiry_times('layout', 'track_number_external_id');
select common.add_version_expiry_times('layout', 'reference_line');
select common.add_version_expiry_times('layout', 'switch');
select common.add_version_expiry_times('layout', 'switch_external_id');
select common.add_version_expiry_times('layout', 'location_track');
select common.add_version_expiry_times('layout', 'location_track_external_id');
select common.add_version_expiry_times('layout', 'km_post');
select common.add_version_expiry_times('layout', 'operating_point');

-- Publication
select common.add_version_expiry_times('publication', 'split');
select common.add_version_expiry_times('publication', 'split_relinked_switch');
select common.add_version_expiry_times('publication', 'split_target_location_track');
select common.add_version_expiry_times('publication', 'split_updated_duplicate');

-- Integrations
select common.add_version_expiry_times('integrations', 'ratko_push_content');

-- Projetkivelho
select common.add_version_expiry_times('projektivelho', 'search');
select common.add_version_expiry_times('projektivelho', 'project_group');
select common.add_version_expiry_times('projektivelho', 'project');
select common.add_version_expiry_times('projektivelho', 'assignment');
select common.add_version_expiry_times('projektivelho', 'project_state');
select common.add_version_expiry_times('projektivelho', 'material_category');
select common.add_version_expiry_times('projektivelho', 'material_group');
select common.add_version_expiry_times('projektivelho', 'material_state');
select common.add_version_expiry_times('projektivelho', 'document_type');
select common.add_version_expiry_times('projektivelho', 'technics_field');
