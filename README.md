# Tarkkailija

Verkkopalvelu asuinympäristösi tapahtumien seurantaan.

Tarkkailijalla löydät muutamalla klikkauksella tietoa valitsemasi alueen asumisesta, kaavoituksesta, liikuntamahdollisuuksista, liikenteestä, yhdistystoiminnasta ja päätöksenteosta. Tarkkailija hakee tiedot yli 400:sta kuntien, viranomaisten ja medioiden verkkopalvelusta.

Tarkkailijalla voit:

* seurata sinua kiinnostavaa tietoa valitsemaltasi alueelta
* asettaa näppärän vahdin seuraamaan sinua kiinnostavia ajankohtaisia asioita
* tilata vahdin koosteena sähköpostiisi
* selata muiden käyttäjien tekemiä vahteja ja jakaa vahtisi muiden hyödyksi

Tarkkailija on toteutettu osana ympäristöministeriön Asumisen ja Rakentamisen ePalvelut -hanketta, jossa tuotetaan asumiseen ja rakennettuun ympäristöön liittyviä sähköisiä asiointi- ja tietopalveluita. Hanke kuuluu valtiovarainministeriön vuonna 2009 käynnistämään Sähköisen asioinnin ja demokratian vauhdittamisohjelmaan (SADe).


# Technical information


## Requirements

A mongodb running on localhost, on default port.

## Setup

cd lein-build-info
lein install
cd ..
cd lein-js-compiler
lein install
cd ..
lein deps

## Usage

lein deps
lein run

### running with embedded mongodb (your existing mongodb needs to be shut down)
lein embongo run


## Testing

### Unit tests
lein midje

#### Note:  By default, integration, system and func tests start their own server with port 8080.

### Integration tests

#### standalone
lein with-profiles dev,itest embongo midje

#### with local mongo
lein with-profiles dev,itest midje

### System tests

#### standalone
lein with-profiles dev,stest embongo midje

#### with local mongo
lein with-profiles dev,stest midje

### Functional / browser tests

#### standalone
lein with-profiles dev,ftest embongo midje

#### with local mongo
lein with-profiles dev,ftest midje

### Midje config

Default config file .midje.clj in project root

* current version just prints facts (easier to verify, which test get stuck, and stuff)
* keep it quiet with :config

lein midje :config

OR use your own confs:

lein midje :config funky-custom-conf.clj

### Development

lein with-profiles dev,alltests eclipse


## Packaging

lein uberjar

## Using it

http://localhost:8080/



# License

Copyright © 2015 Solita

Distributed under the EUPL: "European Union Public Licence" version 1.1.
