/* global Proj4js,OpenLayers */

'use strict';

(function(angular) {

  var module = angular.module('tarkkailija');

  module.service('gis', ['i18n', function(i18n) {
    Proj4js.defs['EPSG:3067'] = '+proj=utm +zone=35 +ellps=GRS80 +units=m +no_defs';

    function Map(element, config) {
      var self = this;

      self.map = new OpenLayers.Map(element, {
        projection: new OpenLayers.Projection("EPSG:3067"),
        units: 'm',
        resolutions : [8192, 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5],
        maxExtent : new OpenLayers.Bounds(-548576.000000,6291456.000000,1548576.000000,8388608.000000),
        theme: '/app/lib/openlayers/theme/default/style.css' // this and...
      });
      // ...this are needed since OpenLayers *sucks* and doesn't do this via classes
      OpenLayers.ImgPath = '/app/lib/openlayers/img/';

      var wmtsServers = ['/api/map'];
      var base = new OpenLayers.Layer('', {displayInLayerSwitcher: false, isBaseLayer: true});

      var taustakartta = new OpenLayers.Layer.WMTS({
          name: "Taustakartta",
          url: wmtsServers,
          isBaseLayer: false,
          transitionEffect: "resize",
          layer: "taustakartta",
          matrixSet: "ETRS-TM35FIN",
          format: "image/png",
          style: "default",
          opacity: 1.0,
          projection: new OpenLayers.Projection("EPSG:3067"),
          resolutions : [8192, 4096, 2048, 1024, 512, 256, 128, 64, 32, 16, 8, 4, 2, 1, 0.5],
          maxExtent : new OpenLayers.Bounds(-548576.000000,6291456.000000,1548576.000000,8388608.000000)
      });

      self.map.addLayers([base, taustakartta]);

      self.geoJsonCallback = config.selectionDone;
      self.config = config;
      self.maxZoomLevel = self.map.getNumZoomLevels() - 3;
      self.filterGeometry = null;


      // Drawings


      self.drawingLayer = new OpenLayers.Layer.Vector('Drawings');
      self.map.addLayer(self.drawingLayer);

      var drawingUpdateCallback = function(param) {
        // If drawing with polygon tool (the multisided one), we have to dig the right geometry from the parents.
        if (param && param.id.indexOf("OpenLayers.Geometry.Polygon") === -1 ) {
          param = param.parent.parent;
        }
        if (param && param.id.indexOf("OpenLayers.Geometry.Polygon") !== -1) {
          if (param.getArea() <= 47000000000) {
            self.drawingLayer.styleMap.styles.temporary.defaultStyle.fillColor = "#66cccc";
            self.drawingLayer.styleMap.styles.temporary.defaultStyle.strokeColor = "#66cccc";
            self.drawingLayer.styleMap.styles['default'].defaultStyle.fillColor = "#ee9900";
            self.drawingLayer.styleMap.styles['default'].defaultStyle.strokeColor = "#ee9900";
          } else {
            self.drawingLayer.styleMap.styles.temporary.defaultStyle.fillColor = "#cc6666";
            self.drawingLayer.styleMap.styles.temporary.defaultStyle.strokeColor = "#cc6666";
            self.drawingLayer.styleMap.styles['default'].defaultStyle.fillColor = "#ee2200";
            self.drawingLayer.styleMap.styles['default'].defaultStyle.strokeColor = "#ee2200";
          }
        }
      };

      self.drawControls = {
        box: new OpenLayers.Control.DrawFeature(
          self.drawingLayer,
          OpenLayers.Handler.RegularPolygon,
          {
            handlerOptions: { sides: 4, irregular: true },
            callbacks: { move:   function(param){ drawingUpdateCallback(param) }}
          }
        )
      };

      var featureAdded = function(event) {
        // clear the previously saved feature
        if(self.drawingLayer.features.length > 1) {
          self.drawingLayer.destroyFeatures(self.drawingLayer.features.shift());
        }

        if(self.geoJsonCallback) {
          if(event.feature.geometry.getArea() <= 47000000000) {
            self.geoJsonCallback(new OpenLayers.Format.GeoJSON().write(event.feature.geometry));
          } else {
            self.geoJsonCallback(null, "oversize");
          }
        }
      };

      for(var key in self.drawControls) {
        self.drawControls[key].events.register('featureadded', self.drawControls[key], featureAdded);
        self.map.addControl(self.drawControls[key]);
      }

      self.toggleControl = function(newActiveControl) {
        for(var key in self.drawControls) {
          var control = self.drawControls[key].deactivate();
        }
        for(var key in self.drawControls) {
          var control = self.drawControls[key];
          if(newActiveControl === key) {
            control.activate();
          }
        }
      };

      self.clearDrawControls = function() {
        if(!_.isEmpty(self.drawingLayer.features)) { self.drawingLayer.destroyFeatures(); }
      };

      self.setFilterGeometry = function(geojson) {
        var geometry = null;
        if(geojson) {
          var parser = new OpenLayers.Format.GeoJSON();
          if(!parser.isValidType(geojson)) { return; }
          geometry = parser.read(geojson, 'Geometry');
        }
        self.filterGeometry = geometry;
      };

      self.updateMapWithGeoDrawing = function(geojson) {
        if(!self.map || !geojson) { return; }

        self.setFilterGeometry(geojson);

        if(self.filterGeometry && self.config.drawingsSupported) {
          self.clearDrawControls();
          self.drawingLayer.addFeatures([new OpenLayers.Feature.Vector(self.filterGeometry)]);
          self.zoomOnCurrentContent();
        }
      };

      self.isInsideFilterGeometry = function(x, y) {
        var insideFilterGeometry = true;
        if(self.filterGeometry) {
          var geometryBounds = self.filterGeometry.getBounds();
          insideFilterGeometry = geometryBounds.contains(x, y);
        }
        return insideFilterGeometry;
      };

      self.getFeatureCount = function() {
        return self.drawingLayer.features.length;
      };


      // Markers


      self.markerInfos = [];

      var defaultStyle = new OpenLayers.Style({
        externalGraphic: 'img/map-marker-blue-2.png',
        graphicWidth: 20,
        graphicHeight: 28,    //alt to pointRadius
        label: '${markerCountLabel}',
        fontColor: 'white',
        fontSize: '16px',
        fontFamily: 'Arial',
        fontWeight: 'bold',
        labelYOffset: 2,
        cursor: 'default'
      }, {
        context: {
          markerCountLabel: function(feature) {
            var uniqueArticlesLength = feature.cluster ?
                _.uniq(feature.cluster, false, function(markerfeature){ return markerfeature.attributes.id; }).length :
                feature.length;
            return (uniqueArticlesLength > 1) ? '' + uniqueArticlesLength : '';
          }
        }
      });
      var selectStyle = new OpenLayers.Style({
        cursor: 'pointer'
      });

      self.markerLayer = new OpenLayers.Layer.Vector('Markers', {
        strategies: [new OpenLayers.Strategy.Cluster({distance: 25/*, threshold: 2*/})],
        styleMap: new OpenLayers.StyleMap({'default': defaultStyle, 'select': selectStyle})
      });
      self.map.addLayer(self.markerLayer);

      // For pop-ups and their selection

      self.gatherHtmlForPopup = function(feature) {
        var onlyUniqueArticles = feature.cluster ?
            _.uniq(feature.cluster, false, function(markerfeature){ return markerfeature.attributes.id; }) :
            feature;

        // FIXME: this HTML generation in js is bad. Externalize to it's own HTML file
        var popupHtml = '';

        var popupTitle = '<h2>' + i18n.getByKey('map.popup.title');
        if(onlyUniqueArticles.length > 1) {
          popupTitle += ' (' + onlyUniqueArticles.length + ')';
        }
        popupTitle += '</h2>';

        _.each(onlyUniqueArticles, function(markerfeature) {
          if(popupHtml.length !== 0) { popupHtml += '<br />'; }
          popupHtml +=
            '<div class="popup-article">' +
              '<a class="selectArticle" href="' + markerfeature.attributes.link + '" target="_blank" title="' + i18n.getByKey('articles.results.show') + '">' +
                '<div class="img-cutout">' +
                  '<img src="' + markerfeature.attributes.picture + '" alt="' + markerfeature.attributes.headline + '" />' +
                '</div>' +
                '<h4><span class="icon-small house inline-left"></span>' + markerfeature.attributes.sourcename + '</h4>' +
                '<time>' + markerfeature.attributes.date + '</time>' +
                '<h4>' + markerfeature.attributes.headline + '</h4>' +
                '<blockquote>' + markerfeature.attributes.description + '</blockquote>' +
              '</a>' +
            '</div>';
        });

        popupHtml = popupTitle + popupHtml;
        return popupHtml;
      };

      self.closePopup = function() {
        if(self.selectedFeature && self.selectedFeature.popup) {
          self.map.removePopup(self.selectedFeature.popup);
          self.selectedFeature.popup.destroy();
          self.selectedFeature = null;
        }
      };

      self.selectedFeature = null;
      self.selectControl = new OpenLayers.Control.SelectFeature(self.markerLayer, {
        autoActivate: true,
        clickOut: true,
        toggle: true,

        onSelect: function(feature) {
          self.selectedFeature = feature;
          var popup = new OpenLayers.Popup.FramedCloud(
              'popup-id',                                           // id (not used)
              feature.geometry.getBounds().getCenterLonLat(),       // lonlat
              null,                                                 // contentSize
              self.gatherHtmlForPopup(feature),                     // (html content)
              null,                                                 // anchor
              true,                                                 // closeBox
              self.closePopup);                                     // closeBoxCallback

          popup.panMapIfOutOfView = true;
          popup.autoSize = true;
          popup.minSize = new OpenLayers.Size(470, 220);
          popup.maxSize = new OpenLayers.Size(550, 550);
          popup.fixedRelativePosition = true;
          feature.popup = popup;
          self.map.addPopup(popup, true);
        }
      });

      self.map.addControl(self.selectControl);


      self.add = function(markerInfos) {
        if(!markerInfos || markerInfos.length === 0) return;

        var addSingle = function(markerInfo) {
          if( self.isInsideFilterGeometry(markerInfo.pos.x, markerInfo.pos.y) ) {
            var geometryPoint = new OpenLayers.Geometry.Point(markerInfo.pos.x, markerInfo.pos.y);
            var markerFeature = new OpenLayers.Feature.Vector(
              geometryPoint, {
                id: markerInfo.id,
                headline: markerInfo.headline,
                link: markerInfo.link,
                sourcename: markerInfo.sourcename,
                description: markerInfo.description,
                picture: markerInfo.picture,
                date: markerInfo.date
              });

            self.markerInfos.push(markerFeature);
          }
        };

        if (_.isArray(markerInfos)) {
          _.each(markerInfos, addSingle);
        } else {
          addSingle(markerInfos);
        }

        self.markerLayer.addFeatures(self.markerInfos);
        return self;
      };

      self.clearMarkers = function() {
        self.markerLayer.removeAllFeatures();
        _.each(self.markerInfos, function(marker) { marker.destroy() });
        self.markerInfos = [];
        return self;
      };

      self.getMarkerCount = function() {
        return self.markerInfos.length;
      };

      // General functions

      self.center = function(location, zoom) {
        if(zoom === undefined) { zoom = 0; }
        self.map.setCenter([location.x, location.y], zoom);
        return self;
      };

      self.rebind = function(div) {
        self.map.render(div);
      };

      self.getConfig = function() {
        return self.config;
      };

      self.setConfig = function(config) {
        if (config.markersSupported === false) {
          self.clearMarkers();
        }
        if (config.drawingsSupported === false) {
          self.clearDrawControls();
        }
        self.config = config;
        self.geoJsonCallback = config.selectionDone;
      };

      self.zoomOnCurrentContent = function() {
        var bounds = null;
        if(self.markerLayer.features.length > 0 && self.config.markersSupported === true) {
          // ** Bug fix hack **  (TODO: Fix this in a better way if possible)
          // The self.markerLayer.getDataExtent() in this block returned wrong boundary area in some cases, and the reason for this
          // seemed to be that self.markerLayer thought it had different amount of markers than it really had.
          // Due to this, "self.map.zoomToExtent(bounds)", called later in this function, zoomed to wrong area.
          // Calling "self.markerLayer.getDataExtent()" one extra time and zooming to that extent, like below, seemed to fix this.
          // On the second time, all markers will really inside the returned bounds, and thus be shown on the map.
          // Do not know the exact reason for this.
          bounds = self.markerLayer.getDataExtent();
          self.map.zoomToExtent(bounds);

          // Extend bounds with the bounds that cover all the markers on the map
          bounds = self.markerLayer.getDataExtent();
        }

        if (self.config.drawingsSupported === true) {
          // Extend bounds with bounds of the features drawn onto map
          var mapFeatures = self.drawingLayer.features;
          if(mapFeatures) {
            if(!_.isArray(mapFeatures)) {
              mapFeatures = [mapFeatures];
            }
            for(var i = 0; i < mapFeatures.length; ++i) {
              if(mapFeatures[i].geometry) {
                if (!bounds) {
                  bounds = mapFeatures[i].geometry.getBounds();
                } else {
                  bounds.extend(mapFeatures[i].geometry.getBounds());
                }
              }
            }
          }
        }

        if(bounds) {
          self.map.zoomToExtent(bounds);

          // Let's prevent map from being (automatically) zoomed too close.
          // User, instead is still able to use all zoom levels.
          // NOTE: self.maxZoomLevel seemed to be initialized falsely rarely, so adding one extra condition for this case.
          var zoomForBounds = self.map.getZoomForExtent(bounds);
          if ( self.maxZoomLevel > 0 && zoomForBounds > self.maxZoomLevel ) {
            zoomForBounds = self.maxZoomLevel;
            self.map.zoomTo(zoomForBounds);
          }
        }
      };

    }

    return {
      makeMap: function(element, config) { return new Map(element, config); }
    };
  }]);

}(angular));