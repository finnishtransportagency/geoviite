create type common.kkj_etrs_triangulation_direction as enum ('KKJ_TO_ETRS', 'ETRS_TO_KKJ');
alter table common.kkj_etrs_triangulation_network
add direction common.kkj_etrs_triangulation_direction not null default 'KKJ_TO_ETRS',
alter column delta_n type decimal(12,4);
