update geometry.plan set source = 'GEOMETRIAPALVELU' where source = 'GEOVIITE';
update geometry.plan_version set source = 'GEOMETRIAPALVELU' where source = 'GEOVIITE';
alter type geometry.plan_source rename to plan_source_old;
create type geometry.plan_source as enum('GEOMETRIAPALVELU', 'PAIKANNUSPALVELU');
alter table geometry.plan alter column source type geometry.plan_source using geometry.plan.source::text::geometry.plan_source;
alter table geometry.plan_version alter column source type geometry.plan_source using geometry.plan_version.source::text::geometry.plan_source;
drop type geometry.plan_source_old;
