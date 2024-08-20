-- Original triangle point data had the N/E ordering of the 3067 coordinates swapped
update common.kkj_etrs_triangle_corner_point
set coord_etrs = postgis.st_setsrid(postgis.st_point(postgis.st_y(coord_etrs), postgis.st_x(coord_etrs)), 3067)
