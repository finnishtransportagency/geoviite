with
  partial_track_number as (
    select track_number.id, track_number.draft
    from layout.track_number
    left join layout.reference_line on reference_line.track_number_id = track_number.id
    where reference_line.id is null
  ),
  new_alignment as (
    insert into layout.alignment(segment_count, length)
    select 0, 0
    from partial_track_number
    returning alignment.id, alignment.version
  )
insert into layout.reference_line(track_number_id, draft, alignment_id, alignment_version, start_address)
select tn.id, tn.draft, a.id, a.version, '0000+0000'
from (select id, draft, row_number() over (order by id) rownum from partial_track_number) tn
inner join (select id, version, row_number() over (order by id) rownum from new_alignment) a on tn.rownum = a.rownum
