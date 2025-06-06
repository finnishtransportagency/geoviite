drop table if exists tmp_faulty_segments;
create temp table tmp_faulty_segments as
select alignment_id, alignment_version, segment_index, switch_id
  from layout.segment_version
  where switch_id is not null
    and switch_start_joint_number = switch_end_joint_number;
alter table tmp_faulty_segments
  add constraint tmp_faulty_segments_pkey primary key (alignment_id, alignment_version, segment_index);

do
$$
  begin
    if exists(
      select *
        from tmp_faulty_segments s
        where exists(
          select *
            from layout.location_track lt
            where lt.alignment_id = s.alignment_id and lt.alignment_version = s.alignment_version
        )
    ) then
      raise exception 'Switch joint errors exist in active location track: fix cannot be applied';
    end if;
  end;
$$;

update layout.segment_version s
set
  switch_start_joint_number =
    case
      when switch_start_joint_number = 1 and switch_id in (696, 1274, 3251) then 2
      else switch_start_joint_number
    end,
  switch_end_joint_number =
    case
      when switch_end_joint_number = 1 and switch_id in (5356) then 2
      else switch_end_joint_number
    end
  where exists(
    select 1
      from tmp_faulty_segments f
      where f.alignment_id = s.alignment_id
        and f.alignment_version = s.alignment_version
        and f.segment_index = s.segment_index
  );

-- The added joint is actually missing from some of the original versions so add them to avoid broken links.
-- This data is picked from the next version of the switch. The location is slightly off (1-2m) but it's what we have.
-- Since it's only in the history anyhow, it should be fine.
with added_joint as (
  select
    joint1_v1.switch_id,
    joint1_v1.switch_layout_context_id,
    joint1_v1.switch_version,
    joint2_v2.number,
    joint2_v2.location,
    joint2_v2.location_accuracy,
    joint2_v2.role
    from layout.switch_version_joint joint1_v1
      inner join layout.switch_version_joint joint2_v2
                 on joint1_v1.switch_id = joint2_v2.switch_id
                   and joint1_v1.switch_layout_context_id = joint2_v2.switch_layout_context_id
                   and joint1_v1.switch_version + 1 = joint2_v2.switch_version
                   and joint2_v2.number = 2
    where joint1_v1.switch_id in (696, 1274, 3251, 5356)
      and joint1_v1.switch_version = 1
      and joint1_v1.number = 1
      and not exists(
      select *
        from layout.switch_version_joint joint2_v1
        where joint1_v1.switch_id = joint2_v1.switch_id
          and joint1_v1.switch_layout_context_id = joint2_v1.switch_layout_context_id
          and joint1_v1.switch_version = joint2_v1.switch_version
          and joint2_v1.number = 2
    )
)
insert
  into layout.switch_version_joint
    (switch_id,
     switch_layout_context_id,
     switch_version,
     number,
     location,
     location_accuracy,
     role)
select
  switch_id,
  switch_layout_context_id,
  switch_version,
  number,
  location,
  location_accuracy,
  role
  from added_joint;

do
$$
  begin
    if exists(
      select *
        from layout.segment_version
        where switch_id is not null
          and switch_start_joint_number = switch_end_joint_number
    ) then
      raise exception 'Not all segments were fixed, please check the data';
    end if;
  end;
$$;
