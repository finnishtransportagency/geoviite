insert into common.coordinate_system(srid, name, aliases)
  values

    -- KKJ zones
    (2391, 'KKJ1', array ['KKJ / Finland zone 1']),
    (2392, 'KKJ2', array ['KKJ / Finland zone 2']),
    (2393, 'KKJ3', array ['KKJ / Finland zone 3', 'KKJ', 'KKJ / Finland Uniform Coordinate System']),
    (2394, 'KKJ4', array ['KKJ / Finland zone 4']),
    (3386, 'KKJ0', array ['KKJ / Finland zone 0']),
    (3387, 'KKJ5', array ['KKJ / Finland zone 5']),

    -- GK__FIN
    (3873, 'GK19', array ['ETRS89 / GK19FIN']),
    (3874, 'GK20', array ['ETRS89 / GK20FIN']),
    (3875, 'GK21', array ['ETRS89 / GK21FIN']),
    (3876, 'GK22', array ['ETRS89 / GK22FIN']),
    (3877, 'GK23', array ['ETRS89 / GK23FIN']),
    (3878, 'GK24', array ['ETRS89 / GK24FIN']),
    (3879, 'GK25', array ['ETRS89 / GK25FIN']),
    (3880, 'GK26', array ['ETRS89 / GK26FIN']),
    (3881, 'GK27', array ['ETRS89 / GK27FIN']),
    (3882, 'GK28', array ['ETRS89 / GK28FIN']),
    (3883, 'GK29', array ['ETRS89 / GK29FIN']),
    (3884, 'GK30', array ['ETRS89 / GK30FIN']),
    (3885, 'GK31', array ['ETRS89 / GK31FIN']),

    -- Older 'ETRS89 / ETRS-GK__FIN', which was later replaced by 'ETRS89 / GK__FIN' (above)
    (3126, 'ETRS-GK19', array ['ETRS89 / ETRS-GK19FIN']),
    (3127, 'ETRS-GK20', array ['ETRS89 / ETRS-GK20FIN']),
    (3128, 'ETRS-GK21', array ['ETRS89 / ETRS-GK21FIN']),
    (3129, 'ETRS-GK22', array ['ETRS89 / ETRS-GK22FIN']),
    (3130, 'ETRS-GK23', array ['ETRS89 / ETRS-GK23FIN']),
    (3131, 'ETRS-GK24', array ['ETRS89 / ETRS-GK24FIN']),
    (3132, 'ETRS-GK25', array ['ETRS89 / ETRS-GK25FIN']),
    (3133, 'ETRS-GK26', array ['ETRS89 / ETRS-GK26FIN']),
    (3134, 'ETRS-GK27', array ['ETRS89 / ETRS-GK27FIN']),
    (3135, 'ETRS-GK28', array ['ETRS89 / ETRS-GK28FIN']),
    (3136, 'ETRS-GK29', array ['ETRS89 / ETRS-GK29FIN']),
    (3137, 'ETRS-GK30', array ['ETRS89 / ETRS-GK30FIN']),
    (3138, 'ETRS-GK31', array ['ETRS89 / ETRS-GK31FIN']),

    -- ETRS89 / TM35FIN
    (3067, 'TM35FIN', array ['ETRS89 / TM35FIN', 'ETRS89 / TM35FIN(E,N)', 'TM35']);
