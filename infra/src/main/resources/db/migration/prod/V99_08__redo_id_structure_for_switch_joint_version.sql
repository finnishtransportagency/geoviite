-- switch joint versions

alter table layout.switch_joint_version add column switch_layout_context_id text;
update layout.switch_joint_version
set switch_layout_context_id = switch_version.layout_context_id
from layout.switch_version
where switch_joint_version.switch_id = switch_version.id
  and switch_joint_version.switch_version = switch_version.version;

alter table layout.switch_joint_version
  drop constraint switch_joint_version_pkey;

-- actual re-versioning happens together with switches, as it depends on switch versions; primary key is added back
-- after re-versioning
