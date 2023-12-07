-- Codes from https://wiki.buildingsmart.fi/fi/04_Julkaisut_ja_Standardit/infrabim_nimikkeisto
create temp table new_feature_type on commit drop as
with temp(code, description) as (
    values
      ('101', 'Mittalinja (tie/katu/vesiväylä)'),
      ('102', 'Ajoradan mittalinja (moniajorataiset)'),
      ('103', 'TSV'),
      ('104', 'Ajoradan keskilinja (suravage)'),
      ('111', 'Pituusmittausraide'),
      ('115', 'Väylälinja haraustasossa, vesiväylän mittalinja'),
      ('116', 'Väylälinjan jatke'),
      ('117', 'Väylän reunalinja haraustasossa'),
      ('120', 'Tien reuna'),
      ('121', 'Keskilinja'),
      ('122', 'Päällysteen reuna'),
      ('123', 'Sisäluiskan yläreuna'),
      ('124', 'Sisäluiskan alareuna'),
      ('125', 'Ulkoluiskan alareuna'),
      ('126', 'Ulkoluiskan yläreuna'),
      ('127', 'Muu pinnan taite'),
      ('130', 'Reunakivilinja, alareuna'),
      ('131', 'Reunakivilinja, yläreuna'),
      ('134', 'Vallin alareuna'),
      ('135', 'Vallin yläreuna'),
      ('140', 'Ojan reuna'),
      ('141', 'Ojan pohja'),
      ('142', 'Ulkoluiskan taite'),
      ('150', 'Rakenneluiskan alareuna'),
      ('151', 'Rakenneluiskan yläreuna'),
      ('152', 'Rakennekerroksen taite'),
      ('153', 'Maalaatikon/penkereen kulma'),
      ('154', 'Palle'),
      ('155', 'Kaivannon alareuna'),
      ('156', 'Kaivannon yläreuna'),
      ('157', 'Rakenneluiskan reuna'),
      ('158', 'Siirtymäkiila'),
      ('159', 'Muu rakenne, reuna'),
      ('192', 'Kallioleikkauksen alareuna'),
      ('193', 'Kallioleikkauksen yläreuna'),
      ('194', 'Kallioleikkauksen taite'),
      ('195', 'Kalliohyllyn ja maaleikkauksen raja'),
      ('196', 'Kalliokaivannon alareuna'),
      ('197', 'Kalliokaivannon yläreuna'),
      ('220', 'Kaide yleensä'),
      ('280', 'Rautatiekiskon selkä'),
      ('281', 'Raiteen keskilinja'),
      ('292', 'Maaliviiva')
)
select *
  from temp;

-- Remove ones that are no longer on the list
delete
  from common.feature_type
  where not exists(select from new_feature_type where new_feature_type.code = feature_type.code);

-- Upsert using 'except' to avoid updating rows that are already identical
insert into common.feature_type(code, description)
select *
  from new_feature_type
except
select code, description
  from common.feature_type
on conflict (code) do update set description = excluded.description;
