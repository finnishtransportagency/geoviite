# Feature: Show Manually Set Duplicates

## Current situation

Geoviitteen nykyisessä versiossa valitun sijaintiraiteen tiedoissa näytetään käyttöliittymässä vain ne duplikaattiraiteet, jotka ovat geometrisesti päällekkäin valitun raiteen kanssa.

## New behavior

Geoviitettä pitäisi muuttaa, niin että käyttöliittymässä näytetään valitulle raiteelle myös manuaalisesti määritetyt duplikaattiraiteet.

## Purpose

Ominaisuus tarvitaan, jotta operaattorikäyttäjät voivat nähdä myös manuaalisesti asetetut duplikaattiraiteet, jotta he
voivat tarvittaessa korjata manuaalisesti asetetun virheellisen tiedon.

## Requirements

Geometrisesti määräytyvien duplikaattiraiteiden yhteydessä esitetään myös manuaalisesti määritetyt duplikaattiraiteet.

Jos jokin esitetyistä duplikaattiraitesta on vain manuaalinen duplikaatti, eli se ei ole geometrinen duplikaatti,
näytetään duplikaattiraiteen vieressä huomiomerkki.

## Technical Tips

Raide määritellään manuaalisesti toisen raiteen duplikaattiraiteeksi duplicateOf ominaisuudella.

Raide voi olla duplikaatti sekä geometrisesti että manuaalisesti.

Raiteet ovat geometrisesti toistensa duplikaatteja, mikäli ne kulkevat kahden tai useamman perättäisen saman
vaihdepisteen
läpi. Tämä tulkinta on jo toteutettu, tätä ei tarvitse muuttaa.

getLocationTrackDuplicates funktiolla on jo manuaaliset duplikaattiraiteet tiedossa, mutta getLocationTrackDuplicatesBySplitPoints funktio palauttaa vain geometriset duplikaatit. Ehkä getLocationTrackDuplicates voisi palauttaa myös manuaaliset duplikaatit, samassa tietorakenteessa kuin geometriset duplikaatit.

## Technical Design

[Tekninen suunnittelu]
