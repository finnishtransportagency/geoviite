alter table geometry.plan drop column bounding_polygon_simple;
alter table geometry.plan add column bounding_polygon_simple postgis.geometry(polygon, 3067)
  generated always as (
    postgis.st_simplifypreservetopology(
        postgis.st_makepolygon(
            postgis.st_exteriorring(
                postgis.st_buffer(bounding_polygon, 500::double precision, 'side=left'::text)
            )
        ),
        150::double precision
    )
  ) stored;
