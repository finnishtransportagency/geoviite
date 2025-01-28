create type common.switch_joint_role as enum ('MAIN', 'CONNECTION', 'MATH');

create temp table structure_details as (
  select
    structure.id,
    structure.presentation_joint_number,
    array_cat(
        array_agg(distinct alignment.joint_numbers[1]),
        array_agg(distinct alignment.joint_numbers[array_length(alignment.joint_numbers, 1)])
    ) as end_joints
    from common.switch_structure structure
      left join common.switch_structure_version_alignment alignment
                on alignment.switch_structure_id = structure.id
                  and alignment.switch_structure_version = structure.version
    group by structure.id
);
alter table structure_details add primary key (id);

alter table layout.switch_version_joint add column role common.switch_joint_role null;
update layout.switch_version_joint joint
set
  role = sub.role
  from (
    select
      joint.switch_id,
      joint.switch_layout_context_id,
      joint.switch_version,
      joint.number,
      case
        when joint.number = structure.presentation_joint_number then 'MAIN'::common.switch_joint_role
        when joint.number = any (structure.end_joints) then 'CONNECTION'::common.switch_joint_role
        when structure.id is not null then 'MATH'::common.switch_joint_role
        -- Sanity check: if structure is not found, default to null -> will fail the migration when column set to not null
      end as role
      from layout.switch_version_joint joint
        left join layout.switch_version switch
                  on switch.id = joint.switch_id
                    and switch.layout_context_id = joint.switch_layout_context_id
                    and switch.version = joint.switch_version
        left join structure_details structure on switch.switch_structure_id = structure.id
  ) sub
  where joint.switch_id = sub.switch_id
    and joint.switch_layout_context_id = sub.switch_layout_context_id
    and joint.switch_version = sub.switch_version
    and joint.number = sub.number;

alter table layout.switch_version_joint
  alter column role set not null;
