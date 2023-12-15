-- The y coordinate of joint number 4 was wrong (zero) in some KV switch types.
-- This migration fixes that problem.

update common.switch_joint
set
  location = postgis.st_setsrid(postgis.st_point(40.920, 1.834), 3067)
  where
      switch_structure_id= (
      select id
        from common.switch_structure where type='KV43-300-1:9,514-O'
    ) and
      number=4
;

update common.switch_joint
set
  location = postgis.st_setsrid(postgis.st_point(40.920, -1.834), 3067)
  where
      switch_structure_id= (
      select id
        from common.switch_structure where type='KV43-300-1:9,514-V'
    ) and
      number=4
;

update common.switch_joint
set
  location = postgis.st_setsrid(postgis.st_point(37.960, 1.833), 3067)
  where
      switch_structure_id= (
      select id
        from common.switch_structure where type='KV30-270-1:9,514-O'
    ) and
      number=4
;

update common.switch_joint
set
  location = postgis.st_setsrid(postgis.st_point(37.960, -1.833), 3067)
  where
      switch_structure_id= (
      select id
        from common.switch_structure where type='KV30-270-1:9,514-V'
    ) and
      number=4
;
