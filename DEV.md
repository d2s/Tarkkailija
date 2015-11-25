# Technical information

## Supported system properties by Tarkkailija

Tarkkailija supports the following sys properties (defined by -D parameters) on service startup.
When developing on local machine you need to set these as system variables on your machine.
The parameters gis.mapserver.url and leiki.url are not necessary to be set.

* **db.url**:  The URL of your Mongo db, e.g "mongodb://localhost/tarkkailija".
* **email.host**:  IP or domain name of the your server.
* **email.user**, email.password:  Username and password for the email connections.
* **email.ssl**:  Boolean - should SSL be used in email sending.
* **feedback.target.email**:  Where to send emails from the feedback functionality (string separated by a comma, with no spaces).
* **watch.cron**:  A string in cron syntax (e.g. "0 9 * * *" to run each day at 9am, only integers to cron parameters). Decides when searches for the saved watches are run and their results sent to users.
* **leiki.url**:  The IP or domain name that provides results for our queries.
* **gis.minimap.url**:  The IP or domain name that provides the minimap pictures for the search results
* **gis.mapserver.url**:  The IP or domain name of the WMTS map server
* **wmts.raster.username**, **wmts.raster.password**:  The credentials with which to get WMTS map raster images from MML.
* **migration.dir**:  The folder that contains your migrations.

## Spatial querys - how Tarkkailija queries search results

* Tarkkailija saves the spatial area to Sito via Sito API and gets a spatial query id and all related postal codes for it
* Tarkkailija saves the spatial query id and all related postal codes within the profile and sends them to Leiki via standard Leiki apis
* Leiki filters results based on all related postal codes and sends them to Sito with the spatial query id for exact matches
* Sito returns results to Leiki which returns them to Tarkkailija


## Requirements for development

A mongodb running on localhost, on default port.

## Setup

```
cd lein-build-info
lein install
cd ..
cd lein-js-compiler
lein install
cd ..
lein deps
```

## Usage

```
lein deps
lein run
```

### Running with embedded mongodb (your existing mongodb needs to be shut down)

```
lein embongo run
```


## Testing

### Unit tests

```
lein midje
```

#### Note:  By default, integration, system and func tests start their own server with port 8080.


### Integration tests

#### standalone
```
lein with-profiles dev,itest embongo midje
```

#### with local mongo
```
lein with-profiles dev,itest midje
```


### System tests

#### standalone
```
lein with-profiles dev,stest embongo midje
```

#### with local mongo

```
lein with-profiles dev,stest midje
```


### Functional / browser tests

#### standalone

```
lein with-profiles dev,ftest embongo midje
```

#### with local mongo

```
lein with-profiles dev,ftest midje
```


### Midje config

Default config file .midje.clj in project root

* current version just prints facts (easier to verify, which test get stuck, and stuff)
* keep it quiet with :config

```
lein midje :config
```

OR use your own confs:

```
lein midje :config funky-custom-conf.clj
```


### Development

```
lein with-profiles dev,alltests eclipse
```


## Packaging

```
lein uberjar
```


## Using it

http://localhost:8080/
