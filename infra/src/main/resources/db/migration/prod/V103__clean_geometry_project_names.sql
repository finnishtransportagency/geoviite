update geometry.plan_project
set name = regexp_replace(
    name,
    -- Remove the "B" letter from the beginning of the name
    '^B',
    ''
  )
  where true;
