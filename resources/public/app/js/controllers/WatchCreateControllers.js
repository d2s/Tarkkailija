/* global Proj4js  */

'use strict';

angular.module('tarkkailija').controller('WizardController',
    ['$rootScope', '$scope', '$location', '$http', '$timeout', 'WizardState', 'Categories', 'MapService', 'FlashMessage', 'i18n',
     function($rootScope, $scope, $location, $http, $timeout, WizardState, Categories, MapService, FlashMessage, i18n) {

  var log = tarkkailija.Utils.getLogger('WizardController');
  var valueKey = function(e) { return e.value; };
  var idKey = function(e) { return e.id; };
  var zoomMap = {"Street section": 14,
                 "Postcode": 11,
                 "Municipality": 9};

  $scope.centerMapToArea = function(area) {
    if(area && area.coordinates) {
      var coordArray = area.coordinates.split(" ").map(Number);
      var zoomLevel = zoomMap[area.type] || 9;
      MapService.centerMap({x: coordArray[0], y: coordArray[1]}, zoomLevel);
    } else {
      FlashMessage.add({type: 'error', content: i18n.getByKey('flash.map.centering.error')});
      log.error("Error in centering map with autocomplete selection, centering to: '" + area.label + "' (" + area.value + ")");
    }
  };

  $scope.areas = WizardState.getSelectedAreas;
  $scope.addArea = function(area) {
    if(area) {
      WizardState.addArea({name: area.label, id: area.value});
    }
    return '';
  };
  $scope.removeArea = function(areaId, fromTempListOnMap) {
    if(fromTempListOnMap === true) {
      for (var i = 0; i < $scope.mapSelectedAreas.length; i++) {
        if ($scope.mapSelectedAreas[i].value === areaId) {
          $scope.mapSelectedAreas.splice(i, 1);
          MapService.updateMapWithContent( $scope.mapSelectedAreas.map(valueKey) );
          break;
        }
      }
    } else {
      WizardState.removeArea(areaId);
    }
  }
  Categories.find(function(cats) {
    $scope.categories = _.sortBy(cats, 'name');
  });
  $scope.toggleCategory = WizardState.toggleCategory;
  $scope.isSelected = WizardState.hasCategory;

  $scope.addWatchCategory = function(cat) {
    if (cat) {
      var transformedCategory = {name: cat.label, id: cat.value};
      WizardState.addCategory(transformedCategory);
      if (!_.some($scope.categories, function(e) { return e.id == transformedCategory.id;})) {
        $scope.categories.push(transformedCategory);
        $scope.categories = _.sortBy($scope.categories, 'name');
      }
    }
    return '';
  };

  $scope.addingWatch = function() {
    return WizardState.getSelectedAreas() > 0;
  };

  $scope.continueWatchCreation = function() {
    $location.path('/search');
  };

  // Map stuff

  $scope.showMap = false;
  $scope.activeMapControl = 'none';
  $scope.mapSelectedAreas = [];
  var closingWithButton = false;
  var areaSearchCounter = 0;

  $scope.$watch('activeMapControl', function(newValue, oldValue) {
    if(newValue && newValue !== oldValue) {
      MapService.toggleControl(newValue);
    }
  });

  $scope.$watch('showMap', function(newValue, oldValue) {
    if(newValue !== oldValue) {
      // Hack: We wait until the dialog is opened (for the first time),
      //          so the dimensions of the map element are available
      //          for successful map creation.
      if (newValue === true) {
        _.each(WizardState.getSelectedAreas(), function(area) {
          $scope.mapSelectedAreas.push({label: area.name, value: area.id});
        });

        MapService.setupMap('frontpage-map', {
          markersSupported: false,
          drawingsSupported: true,
          selectionDone: function(geojson, error) {
            if(geojson === null && error === "oversize") {
              FlashMessage.add({type: 'error', content: i18n.getByKey('flash.map.selected.too.big.area')});
              log.error("User tried to select to big area from map");
              return;
            }

            areaSearchCounter = areaSearchCounter + 1;
            $scope.mapAreaSearchInProgress = true;
            MapService.getAreasForGeojson(geojson, $scope.mapSelectedAreas, function(newAreas) {
              areaSearchCounter = areaSearchCounter - 1;

              if(areaSearchCounter === 0) {
                $scope.mapAreaSearchInProgress = false;
                if(newAreas && newAreas.length > 0) {
                  $scope.mapSelectedAreas = newAreas;
                }
                // get the exact geojson for all the areas that are now selected
                MapService.updateMapWithContent( $scope.mapSelectedAreas.map(valueKey) );
              }
            });

            $scope.$apply();
          }
        });

      } else {
        $scope.mapSelectedAreas = [];
        $scope.activeMapControl = 'none';

        if (!closingWithButton) {
          MapService.updateMapWithContent( WizardState.getSelectedAreas().map(idKey) );
        }
        closingWithButton = false;

      }
    }
  });

  $scope.controlSelectedClass = function(mapControl) {
    return mapControl === $scope.activeMapControl ? 'on' : 'off';
  };

  $scope.toggleActiveMapControl = function(newMapControl) {
    if(newMapControl) {
      if(newMapControl !== $scope.activeMapControl) {
        $scope.activeMapControl = newMapControl;
      } else if($scope.activeMapControl !== 'none') {
        $scope.activeMapControl = 'none';
      }
    }
  };

  $scope.clearMapDrawings = function() {
    MapService.clearMarkers();
    MapService.clearDrawControls();
    $scope.mapSelectedAreas = [];
  };

  $scope.cancelMapSelection = function() {
    closingWithButton = true;
    $scope.mapSelectedAreas = [];
    MapService.updateMapWithContent( WizardState.getSelectedAreas().map(idKey) );
  };

  $scope.acceptMapSelection = function() {
    closingWithButton = true;
    WizardState.removeAllAreas();
    for ( var i = 0; i < $scope.mapSelectedAreas.length; i++) {
      $scope.addArea($scope.mapSelectedAreas[i]);
    }
    MapService.updateMapWithContent( WizardState.getSelectedAreas().map(idKey) );
  };

}]);

angular.module('tarkkailija').controller('SearchController',
    ['$scope', '$rootScope', '$routeParams', '$http', '$location', '$cookies', 'Categories', 'WizardState', 'MapService', 'FlashMessage', 'i18n',
     function($scope, $rootScope, $routeParams, $http, $location, $cookies, Categories, WizardState, MapService, FlashMessage, i18n) {

  var log = tarkkailija.Utils.getLogger('SearchController');
  var dates = tarkkailija.dates;

  $scope.lang = $cookies.lang;

  WizardState.setDirty(false);

  var ranges = {
    'everything': 0,
    'fiveyears': dates.fromNow(dates.minusYears, 5),
    'year': dates.fromNow(dates.minusYears, 1),
    'month': dates.fromNow(dates.minusMonths, 1),
    'week': dates.fromNow(dates.minusDays, 7)
  };

  $scope.timeRangeValues = _.chain(ranges).pairs().map(function(e) {
    return {label: i18n.getByKey('timerange.' + e[0]), value: e[1]};
  }).value();

  $scope.timeRange = $routeParams.range && ranges[$routeParams.range] ? ranges[$routeParams.range] : ranges.year; // default one year

  // FIXME: duplication! Whole map dialog thingy should be separated into own controller + html
  $scope.controlSelectedClass = function (mapControl) {
    return mapControl === $scope.activeMapControl ? 'on' : 'off';
  };

  var nameComparator = function (a, b) { return a.name > b.name; };

  var combineCategories = function (a, b) {
    var result = a;
    for (var i = 0; i < b.length; i++) {
      if (!_.some(result, function(e) { return e.id == b[i].id; })) { result.push(b[i]); }
    }
    return result;
  };

  var mergeCategories = function(/*arguments*/) {
    var args = Array.prototype.slice.call(arguments);

    var result = args[0];

    for (var i = 1; i < args.length; i++) {
      result = combineCategories(result, args[i]);
    }

    return result;
  };

  $scope.categories = [];

  Categories.find(function (cats) {
    $scope.categories = mergeCategories($scope.categories, WizardState.getSelectedCategories(), cats).sort(nameComparator);
  });
  $scope.isSelected = WizardState.hasCategory;
  $scope.areas = WizardState.getSelectedAreas;
  $scope.isPublic = WizardState.isPublicWatch;
  $scope.makePublic = WizardState.setAsPublic;
  $scope.makePrivate = WizardState.setAsPrivate;
  $scope.watchUpdateOrCreate = 'update';

  $scope.resetWatch = function () {
    $scope.clearMapDrawings();
    WizardState.clear();
    $location.path('/search');
    $scope.search(false);
  };

  $scope.newWatchClicked = function () {
    if (WizardState.isDirty()) {
      $scope.newWatchConfirmation = true;  //opens confirmation dialog
    } else {
      $scope.resetWatch();
    }
  };

  var forceSearch = false;

  (function initStateFromUrlParams(params) {
    var watchId = $location.path().substr('/search/'.length);

    var initEmptyPage = function () {
      WizardState.clear();
      if (params.name) {
        WizardState.setName(params.name);
      }
      if (params.areas) {
        $http.get('/api/areas-with-term?term=' + params.areas.split(',')[0]).success(function (data) {
          if (data && data.length > 0) { $scope.addWatchArea(data[0]); }
        });
      }
      if (params.categories) {
        $http.get('/api/categories?term=' + params.categories.split(',')[0]).success(function (data) {
          if (data && data.length > 0) { $scope.addWatchCategory(data[0]); }
        });
      }
    };

    var initPageForWatch = function () {
      $http.get('/api/watches/' + watchId).success(function (data) {
        $scope.watchId = watchId;

        $scope.categories = mergeCategories($scope.categories, data.watch.categories).sort(nameComparator);

        WizardState.clear();
        WizardState.initFrom(data.watch);
        $scope.watchName = data.watch.name;
        $scope.watch = data.watch;
        $scope.search(false);
      }).error(function (msg, status) {
        // when status is 401, we want to show the user a login dialog
        if (status !== 401) {
          $location.path('/search');
        } else {
          // TODO: flash error
        }
      });
    };

    if (watchId.length === 0 && (params.areas || params.categories)) {
      initEmptyPage();
    } else if (watchId.length > 0) {
      initPageForWatch();
    } else {
      forceSearch = true;
    }

    WizardState.setDirty(false);

  } ($location.search()));

  $scope.$watch('user', function (newValue, oldValue) {
    if (newValue && newValue !== oldValue) {
      refreshWatches();
    }
  });

  $scope.$watch('watch', function (newValue, oldValue) {
    if (newValue && newValue !== oldValue) {
      $scope.data = _.clone(newValue);
    }
  });

  $scope.setWatchName = function () {
    WizardState.setName($scope.watchName);
  };

  $scope.getWatchName = function () {
    return WizardState.getName();
  };

  $scope.getWatchNameForTitle = function () {
    if (!_.isEmpty(WizardState.getName())) {
      return WizardState.getName();
    } else if (!$rootScope.isLogged() || ($scope.isSavedWatch() && !$scope.isOwnWatch())) {
      return i18n.getByKey('general.search');
    } else {
      return '<' + i18n.getByKey('watch.noname') + '>';
    }
  };

  $scope.rssLink = function () {
    if (WizardState.isPublicWatch()) {
      return _.strFormat("/api/watches/{0}/rss", WizardState.getId());
    }
    return _.strFormat("/api/watches/{0}/rss?token={1}", WizardState.getId(), encodeURIComponent(WizardState.getRssToken()));
  };

  $scope.setSubscribeEmail = function () {
    if (!$scope.subscription.email || !$rootScope.user || !$rootScope.user.email) { return; }

    var emailToStore = null;
    if ($scope.subscription && $scope.subscription.email !== $rootScope.user.email) {
      emailToStore = $scope.subscription.email;
    }
    if ($scope.isSavedWatch()) {
      $http.put('/api/subscribes', { watchId: $scope.watch._id, email: emailToStore, lang: $scope.subscription.lang }).success(function () {
        WizardState.setSubscribed(true);
        FlashMessage.add({ type: 'info', content: i18n.getByKey('flash.subscribe.successful') });
      }).error(function (data, status) {
        FlashMessage.add({ type: 'error', content: i18n.getByKey('flash.subscribe.error') });
        log.error(data, status);
      });
    }
  };

  $scope.removeSubscribeEmail = function () {
    if ($scope.isSavedWatch()) {
      $http['delete']('/api/subscribes/' + $scope.watch._id).success(function () {
        WizardState.setSubscribed(false);
        FlashMessage.add({ type: 'info', content: i18n.getByKey('flash.watch.unsubscribe.successful') });
      }).error(function (data, status) {
        FlashMessage.add({ type: 'error', content: i18n.getByKey('flash.watch.delete.error') });
        log.error(data, status);
      });
    }
  };

  $scope.addArea = function (area) {
    if (area) {
      WizardState.addArea({ name: area.label, id: area.value });
    }
    return '';
  };

  $scope.addWatchArea = function (area) {
    if (area) {
      $scope.addArea(area);
      $scope.search(false);
    }
    return '';
  };

  $scope.removeArea = function (areaId, fromTempListOnMap) {
    if (fromTempListOnMap === true) {
      for (var i = 0; i < $scope.mapSelectedAreas.length; i++) {
        if ($scope.mapSelectedAreas[i].value === areaId) {
          $scope.mapSelectedAreas.splice(i, 1);
          MapService.updateMapWithContent($scope.mapSelectedAreas.map(valueKey), true);
          break;
        }
      }
    } else {
      WizardState.removeArea(areaId);
      $scope.search(false);
    }
  }

  $scope.addWatchCategory = function (cat) {
    if (cat) {
      var transformedCategory = { name: cat.label, id: cat.value };
      WizardState.addCategory(transformedCategory);
      if (!_.some($scope.categories, function(e) { return e.id == transformedCategory.id;})) {
        $scope.categories.push(transformedCategory);
        $scope.categories = _.sortBy($scope.categories, 'name');
      }
      $scope.search(false);
    }
    return '';
  };

  $scope.isSavedWatch = function () {
    return $scope.watch !== undefined && WizardState.isPersisted();
  };

  $scope.isValidWatch = function () {
    return WizardState.isValid();
  };

  $scope.isOwnWatch = function () {
    if ($rootScope.user !== undefined && $scope.isSavedWatch() && WizardState.isOwnWatch()) { return true; }
    return false;
  };

  $scope.isSubscribed = function () {
    return $rootScope.user !== undefined && $scope.isSavedWatch() && WizardState.isSubscribed();
  };

  function emptyWatch() {
    return { name: '', email: $rootScope.user && $rootScope.user.email, categories: [], term: '', emailChecked: false };
  }

  $scope.hasWatches = function () {
    return !_.isEmpty($scope.watches);
  };

  function refreshWatches(id) {
    $http.get('/api/watches').success(function (data) {
      $scope.watches = data.watches;
      if ($scope.watch === undefined || id) {
        if (id) {
          $scope.watch = _.find($scope.watches, function (watch) { return watch._id === id; });
        } else {
          $scope.watch = _.first($scope.watches);
        }
      }
      if ($scope.watch !== undefined) {
        $scope.data = _.clone($scope.watch);
      } else {
        $scope.data = emptyWatch();
      }
    });
  }

  $scope.saveDialog = function () {

  }

  $scope.saveWatch = function () {
    $scope.setWatchName();
    if (WizardState.isPersisted()) {
      update(WizardState.getFullState());
    } else {
      create(WizardState.getFullState());
    }
    WizardState.setDirty(false);
  };

  function create(data) {
    $http.post('/api/watches', data).success(function (data) {
      $location.path('/search/' + data.watch._id);
      FlashMessage.add({ type: 'info', content: i18n.getByKey('flash.watch.new.save.successful', WizardState.getName()) });
    }).error(function (data, status) {
      FlashMessage.add({ type: 'error', content: i18n.getByKey('flash.watch.save.error') });
      log.error('Error in watch create', data, status);
    });
  }

  function update(data) {
    $http.put('/api/watches', data).success(function (data) {
      FlashMessage.add({ type: 'info', content: i18n.getByKey('flash.watch.modification.save.successful', WizardState.getName()) });
      refreshWatches(data.watch._id);
    }).error(function (data, status) {
      FlashMessage.add({ type: 'error', content: i18n.getByKey('flash.watch.save.error') });
      log.error('Error in watch update', data, status);
    });
  }

  $scope.deleteActiveWatch = function () {
    $http['delete']('/api/watches/' + WizardState.getId()).success(function () {
      WizardState.clear();
      $location.path('/search');
    });
  };

  $scope.like = function (article) {
    var like = !(article.personal && article.personal.likes);
    $http.post('/api/articles/like', { id: article.id, like: like }).success(function (data) {
      article.likes = data.likes;
      article.personal = { likes: like };
    });
  };

  var idKey = function (e) { return e.id; };
  var valueKey = function (e) { return e.value; };
  var loadingMoreArticles = false;

  $scope.changeTimeStart = function () {
    $scope.search(false);
  };

  $scope.articles = [];
  $scope.totalresults = 0;
  $scope.articlesOnAPage = 20;
  $scope.offset = 0;
  $scope.limit = $scope.articlesOnAPage;

  $scope.getNextPageful = function () {
    $scope.offset += $scope.articlesOnAPage;
    $scope.limit += $scope.articlesOnAPage;
    $scope.search(true);
  };

  var latestRequestParams = {};

  $scope.search = function (keepPreviousResults) {
    $scope.searchLoading = true;
    keepPreviousResults = keepPreviousResults || false;
    if (keepPreviousResults) {
      loadingMoreArticles = true;
    } else {
      loadingMoreArticles = false;
      $scope.offset = 0;
      $scope.limit = $scope.articlesOnAPage;
    }

    (function () {
      var requestParams = _.clone({
        areas: WizardState.getSelectedAreas().map(idKey),
        categories: WizardState.getSelectedCategories().map(idKey),
        email: $rootScope.user && $rootScope.user.email,
        startdatemillis: $scope.timeRange,
        limit: $scope.limit,
        offset: $scope.offset
      });
      latestRequestParams = requestParams;

      $http.post('/api/feed', requestParams)
      .success(function (data) {

        data.articles = data.articles || [];

        // sanity check, our requestParams should be same as latest, if not then our
        // received data is already out of date -> dump it
        if (_.isEqual(requestParams, latestRequestParams)) {
          $scope.searchLoading = false;
          if (loadingMoreArticles) {
            loadingMoreArticles = false;
          } else {
            $scope.articles = [];
          }

          var articlesWithDescTrimmed = _.chain(data.articles)
             .sortBy('date').reverse()
             .map(function (elem) {
               var e = elem;
               if (e.description && e.description.length >= 280) {
                 e.description = e.description + '...';
               }
               return e;
             })
             .map(function (elem) {
               return _.extend(elem, { id: encodeURIComponent(elem.id) });
             })
             .value();
          $scope.articles = $scope.articles.concat(articlesWithDescTrimmed);
          $scope.totalresults = data.totalresults || 0;
          $scope.$broadcast('searchDone');
        } else {
          log.debug('Deprecated response, ignoring');
        }
      }).error(function (data, status, headers) {
        // same sanity check here, let the error pass to logs however
        if (_.isEqual(requestParams, latestRequestParams)) {
          $scope.searchLoading = false;
          FlashMessage.add({ type: 'error', content: i18n.getByKey('flash.search.error') });
        }
        log.error('Error while fetching articles', data, status, headers);
      });
    }());

    MapService.clearDrawControls();
  };

  $scope.toggleCategory = function (cat) {
    WizardState.toggleCategory(cat);
    $scope.search(false);

  };

  if (forceSearch) { $scope.search(false); }

  $scope.visible = 'text';

  $scope.isTabVisible = function (type) {
    return $scope.visible === type;
  };

  $scope.showTab = function (type) {
    $scope.visible = type;

    if (type === 'map') {
      MapService.setupMap('search-map', {
        markersSupported: true,
        drawingsSupported: false
      });
    }
  };

  $scope.$on('searchDone', function () {
    // Let's ensure that map does not stay visible unintentionally
    if ($scope.totalresults == 0) {
      $scope.showTab('text');
    }
    MapService.updateMarkers($scope.articles);
  });

  // For the map dialog

  // TODO: Some repetition here compared to WizardController -> merge these codes somehow

  $scope.showMap = false;
  $scope.activeMapControl = 'none';
  $scope.mapSelectedAreas = [];
  var closingWithButton = false;
  var areaSearchCounter = 0;

  $scope.$watch('activeMapControl', function (newValue, oldValue) {
    if (newValue && newValue !== oldValue) {
      MapService.toggleControl(newValue);
    }
  });

  $scope.$watch('showMap', function (newValue, oldValue) {
    if (newValue !== oldValue) {
      // Hack: We wait until the dialog is opened (for the first time),
      //          so the dimensions of the map element are available
      //          for successful map creation.
      if (newValue === true) {
        _.each(WizardState.getSelectedAreas(), function (area) {
          $scope.mapSelectedAreas.push({ label: area.name, value: area.id });
        });

        MapService.setupMap('resultpage-map', {
          markersSupported: true,
          drawingsSupported: true,
          selectionDone: function (geojson, error) {
            if (geojson === null && error === "oversize") {
              FlashMessage.add({ type: 'error', content: i18n.getByKey('flash.map.selected.too.big.area') });
              log.error("User tried to select to big area from map");
              return;
            }

            areaSearchCounter = areaSearchCounter + 1;
            $scope.mapAreaSearchInProgress = true;
            MapService.getAreasForGeojson(geojson, $scope.mapSelectedAreas, function (newAreas) {
              areaSearchCounter = areaSearchCounter - 1;

              if (areaSearchCounter === 0) {
                $scope.mapAreaSearchInProgress = false;
                if (newAreas && newAreas.length > 0) {
                  $scope.mapSelectedAreas = newAreas;
                }
                // get the exact geojson for all the areas that are now selected
                MapService.updateMapWithContent($scope.mapSelectedAreas.map(valueKey));
              }
            });

            $scope.$apply();
          }
        });

      } else {
        $scope.mapSelectedAreas = [];
        $scope.activeMapControl = 'none';

        if (!closingWithButton) {
          MapService.updateMapWithContent(WizardState.getSelectedAreas().map(idKey));
        }
        closingWithButton = false;

        if ($scope.visible === 'map') {
          MapService.setupMap('search-map', {
            markersSupported: true,
            drawingsSupported: false
          });
        }
      }
    }
  });

  $scope.toggleActiveMapControl = function (newMapControl) {
    if (newMapControl) {
      if (newMapControl !== $scope.activeMapControl) {
        $scope.activeMapControl = newMapControl;
      } else if ($scope.activeMapControl !== 'none') {
        $scope.activeMapControl = 'none';
      }
    }
  };

  $scope.clearMapDrawings = function () {
    MapService.clearMarkers();
    MapService.clearDrawControls();
    $scope.mapSelectedAreas = [];
  };

  $scope.cancelMapSelection = function () {
    $scope.mapSelectedAreas = [];
    closingWithButton = true;
    MapService.updateMapWithContent(WizardState.getSelectedAreas().map(idKey));
  };

  $scope.acceptMapSelection = function () {
    closingWithButton = true;
    WizardState.removeAllAreas();
    for (var i = 0; i < $scope.mapSelectedAreas.length; i++) {
      $scope.addArea($scope.mapSelectedAreas[i]);
    }
    $scope.search(false);
    MapService.updateMapWithContent(WizardState.getSelectedAreas().map(idKey), true);
  };

}]);


angular.module('tarkkailija').service('MapService',
    ['$rootScope', '$http', 'WizardState', 'FlashMessage', 'i18n', 'gis',
     function($rootScope, $http, WizardState, FlashMessage, i18n, gis) {

  var log = tarkkailija.Utils.getLogger('MapService');
  var idKey = function(e) { return e.id; };
  var valueKey = function(e) { return e.value; };

  var initialState = function() {
    return {
      map: null,
      element: 'frontpage-map',
      mapConfig: {
        markersSupported: false,
        drawingsSupported: false,
        selectionDone: function() {}
      },
      // If we have received an articles update before the map instance is created,
      // let's store the marker infos of those articles here,
      // and apply them onto the map once the map is created.
      markerInfos: []
    };
  };

  var state = initialState();
  var defaultCenterPointForEmptyMap = {x: 442001.164209, y: 7144793.630223};

  var centerMap = function(locations, zoomLevel) {
    if (!state.map) { return; }

    locations = locations || defaultCenterPointForEmptyMap;
    if(zoomLevel === undefined) { zoomLevel = 0; }

    if(_.isArray(locations)) {
      // Calculate an average point from all the received points.
      var loc = defaultCenterPointForEmptyMap;
      var sumX = 0;
      var sumY = 0;
      _.each(locations, function(location) {
        sumX += location.x;
        sumY += location.y;
      });
      loc.x = sumX / locations.length;
      loc.y = sumY / locations.length;

      state.map.center(loc, zoomLevel);
    } else {
      state.map.center(locations, zoomLevel);
    }
  };

  var centerMapToCurrentPos = function(zoomLevel) {
    if (!state.map) { return; }
    if (zoomLevel === undefined) { zoomLevel = 0; }

    // This ensures displaying of the map in the case where user just closes the browser's geolocation query note from the cross (or ignores it and it disappears).
    centerMap();

    var errorCallback = function(error) {
      // ignoring the error -> show whole finland on map
      tarkkailija.Utils.getLogger('MapService').error('Error in getting geolocation from browser: ', error);
      centerMap();
    };

    if (navigator && navigator.geolocation && navigator.geolocation.getCurrentPosition) {
      navigator.geolocation.getCurrentPosition(function(position) {
        centerMap(Proj4js.transform(Proj4js.WGS84,
                                    new Proj4js.Proj('EPSG:3067'),
                                    {x: position.coords.longitude, y: position.coords.latitude}),
                                    zoomLevel);
      }, errorCallback);
    } else {
      centerMap(); // no params -> uses the default location
    }
  };

  var getConfig = function() {
    return state.map.getConfig();
  };

  var setConfig = function(config) {
    state.map.setConfig(config);
  };

  var ripLocationsFromArticles = function(articles) {
    var markerInfos = [];
    _.each(articles, function(article) {
      _.each(article.location, function(location) {
        markerInfos.push({
          id: article.id,
          pos: (new Proj4js.Point(location.x, location.y)),
          headline: article.headline,
          link: article.link,
          sourcename: article.sourcename,
          description: article.description,
          picture: article.picture,
          date: $.datepicker.formatDate('dd.mm.yy', new Date(article.date))
        });
      });
    });
    return markerInfos;
  };

  var clearMarkers = function() {
    if (state.map) state.map.clearMarkers();
  };

  var updateMarkers = function(articles) {
    state.markerInfos = ripLocationsFromArticles(articles);
    // Content is zoomed to when opening map, but this is needed when the map is already visible.
    updateMapWithContent( WizardState.getSelectedAreas().map(idKey), true );
  };

  var clearDrawControls = function() {
    if(state.map) {
      state.map.closePopup();
      state.map.clearDrawControls();
    }
  };

  var toggleControl = function(newValue) {
    if(state.map) {
      state.map.toggleControl(newValue);
    }
  };

  var doCreateMap = function() {
    if (state.element && state.mapConfig) {
      state.map = gis.makeMap( state.element, state.mapConfig );
      // This updates map's marker filter, so this needs to be before adding markers
      updateMapWithContent( WizardState.getSelectedAreas().map(idKey), true );
    }
  };

  var resetMap = function() {
    state.map.rebind(state.element);
    state.map.setConfig(state.mapConfig);
    state.map.closePopup();

    // This updates map's marker filter, so this needs to be before adding markers
    updateMapWithContent( WizardState.getSelectedAreas().map(idKey), true );
  };

  var setupMap = function(element, config) {
    state.element = element;
    state.mapConfig = config;

    var callback;
    if (state.map) {
      callback = resetMap;
    } else {
      callback = doCreateMap;
    }
    $rootScope.callWhenVisible('#'+element, callback);
  };

  var getAreasForGeojson = function(geojson, currentAreas, callbackFunc) {
    if(geojson && geojson.length > 0) {
      $http.get('/api/areas-with-geojson?geojson=' + geojson)
      .success(function(data) {
        if (data && data.length > 0) {
          var areas = _.union(currentAreas, data);
          areas = _.uniq(areas, false, valueKey);
          callbackFunc(areas);
        } else {
          callbackFunc([]);
        }
      })
      .error(function(data, status) {
        FlashMessage.add({type: 'error', content: i18n.getByKey('flash.geojson.to.areas.receiving.error')});
        log.error(data, status);
        callbackFunc([]);
      });
    } else {
      callbackFunc([]);
    }
  };

  var geojsonRequCounter = 0;
  var updateMapWithContent = function(areaIds, alsoUpdateMarkers) {
    if (!state.map) { return; }
    alsoUpdateMarkers = alsoUpdateMarkers || false;

    if(!areaIds || areaIds.length === 0) {
      clearDrawControls();

      if(alsoUpdateMarkers && state.mapConfig.markersSupported === true) {
        clearMarkers();
        if (state.markerInfos.length > 0) {
          state.map.add(state.markerInfos);
          state.map.zoomOnCurrentContent();
        } else {
          centerMapToCurrentPos(9);
        }
      } else {
        centerMapToCurrentPos(9);
      }
      return;
    }

    geojsonRequCounter = geojsonRequCounter + 1;
    $http.get('/api/exactgeojson?areas=' + areaIds)
    .success(function(geojsonData) {
      geojsonRequCounter = geojsonRequCounter - 1;

      if(geojsonData === "null") {
        // No geojson received for the areaIds, let's remove markers and drawing forms from map.
        clearMarkers();
        clearDrawControls();
        centerMapToCurrentPos(9);
        if (geojsonRequCounter === 0) {
          FlashMessage.add({type: 'error', content: i18n.getByKey('flash.no.exact.geojson.found.error')});
        }
        return;
      }

      geojsonData = JSON.stringify(geojsonData);
      if (geojsonData && geojsonData.length > 0) {

        // Put the new drawing feature to the map.
        state.map.updateMapWithGeoDrawing(geojsonData);

        // Filter markers based on the now updated filter area (the new geojson).
        if(alsoUpdateMarkers && state.mapConfig.markersSupported === true) {
          clearMarkers();
          if (state.markerInfos.length > 0) {
            state.map.add(state.markerInfos);  //Marker filttering happens inside add()
          }
        }

        // Zoom into content, or into current user location if no map features exist.
        if(state.map.getMarkerCount() > 0 || state.map.getFeatureCount() > 0) {
          state.map.zoomOnCurrentContent();
        } else {
          centerMapToCurrentPos(9);
        }
      }
    })
    .error(function(data, status) {
      geojsonRequCounter = geojsonRequCounter - 1;
      if(geojsonRequCounter === 0) {
        FlashMessage.add({type: 'error', content: i18n.getByKey('flash.exact.geojson.receiving.error')});
      }
      log.error(data, status);
    });
  };

  return {
    setupMap: setupMap,
    getConfig: getConfig,
    setConfig: setConfig,
    clearMarkers: clearMarkers,
    updateMarkers: updateMarkers,
    clearDrawControls: clearDrawControls,
    toggleControl: toggleControl,
    centerMap: centerMap,
    getAreasForGeojson: getAreasForGeojson,
    updateMapWithContent: updateMapWithContent
  };

}]);


angular.module('tarkkailija').service('WizardState', [function () {

  var initialState = function () {
    return {
      dirty: false,
      name: '',
      id: null,
      areas: [],
      categories: [],
      publicFeed: false,
      ownWatch: true,
      subscribed: false
    };
  };

  var state = initialState();

  var idPredicate = function(obj) {
    return function(e) {
      return e.id == obj.id;
    };
  };

  var setDirty = function (dirty) {
    state.dirty = dirty;
  };

  var isDirty = function () {
    return state.dirty;
  };

  var setName = function (name) {
    setDirty(true);
    state.name = name;
  };

  var getName = function () {
    return state.name;
  };

  var isPersisted = function () {
    return state.id != null;
  };

  var addArea = function (area) {
    setDirty(true);
    if (area.id && area.id.length > 0 && !_.some(state.areas, idPredicate(area))) { state.areas.push(area); }
  };

  var addAreas = function (newAreas) {
    setDirty(true);
    _.each(newAreas, addArea);
  };

  var removeArea = function (areaId) {
    setDirty(true);
    for (var i = 0; i < state.areas.length; i++) {
      if (state.areas[i].id === areaId) {
        state.areas.splice(i, 1);
        break;
      }
    }
  };

  var removeAllAreas = function () {
    setDirty(true);
    state.areas = [];
  };

  var addCategory = function (cat) {
    setDirty(true);
    if (cat.id && cat.id.length > 0 && !_.some(state.categories, idPredicate(cat))) { state.categories.push(cat); }
  };

  var addCategories = function (newCategories) {
    setDirty(true);
    _.forEach(newCategories, addCategory);
  };

  var findCategoryIndex = function (cat) {
    for (var i = 0; i < state.categories.length; i++) {
      if (state.categories[i].id === cat.id) {
        return i;
      }
    }
    return -1;
  };

  var removeCategory = function (cat) {
    var index = findCategoryIndex(cat);
    if (index >= 0) {
      state.categories.splice(index, 1);
    }
  };

  var hasCategory = function (cat) {
    return findCategoryIndex(cat) >= 0;
  };

  var toggleCategory = function (cat) {
    setDirty(true);

    if (hasCategory(cat)) {
      removeCategory(cat);
    } else {
      addCategory(cat);
    }
  };

  var getSelectedAreas = function () { return state.areas; };

  var getSelectedCategories = function () { return state.categories; };

  var isEmpty = function () {
    return state.areas.length === 0 && state.categories.length === 0;
  };

  var setAsPublic = function () {
    setDirty(true);
    state.publicFeed = true;
  };

  var setAsPrivate = function () {
    setDirty(true);
    state.publicFeed = false;
  };

  var setSubscribed = function (isSubscribed) {
    state.subscribed = isSubscribed;
  };

  var initFrom = function (watch) {
    addAreas(watch.areas);
    addCategories(watch.categories);
    setName(watch.name);
    if (watch.publicFeed) {
      setAsPublic();
    } else {
      setAsPrivate();
    }
    state.ownWatch = watch.ownWatch;
    state.subscribed = watch.subscribed;
    state.id = watch._id;

    state.rssToken = watch['rss-token'];

    setDirty(false);
  };

  return {
    setDirty: setDirty,
    isDirty: isDirty,
    getId: function () { return state.id; },
    setName: setName,
    getName: getName,
    addArea: addArea,
    addAreas: addAreas,
    removeArea: removeArea,
    removeAllAreas: removeAllAreas,
    hasCategory: hasCategory,
    toggleCategory: toggleCategory,
    addCategory: addCategory,
    addCategories: addCategories,
    getSelectedAreas: getSelectedAreas,
    getSelectedCategories: getSelectedCategories,
    isValid: function () { return !isEmpty() && state.name.length > 0; },
    clear: function () { state = initialState(); },
    initFrom: initFrom,
    isEmpty: isEmpty,
    isPersisted: isPersisted,
    getFullState: function () { return _.omit(state, 'dirty'); },
    isPublicWatch: function () { return state.publicFeed; },
    setAsPublic: setAsPublic,
    setAsPrivate: setAsPrivate,
    isOwnWatch: function () { return state.ownWatch; },
    setSubscribed: setSubscribed,
    isSubscribed: function () {
      return state.subscribed; },
    getRssToken: function () { return state.rssToken; }
  };
} ]);
