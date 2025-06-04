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
