'use strict';

angular.module('tarkkailija').controller('WatchBrowseController', 
    ['$rootScope', '$scope', '$location', '$http', '$cookies',
     function($rootScope, $scope, $location, $http, $cookies) {
      
  var log = tarkkailija.Utils.getLogger('WatchBrowseController');

  var publicWatchLimit = 32;
  $scope.currentSkip = 0;

  var convertDate = function(w) {
    return _.omit(_.extend(w, {created: new Date(w._created)}), "_created");
  };

  var combinedTransforms = _.compose(convertDate);
  
  $http.get('/api/watches')
    .success(function(data) {
      $scope.ownWatches = _.map(data.watches, combinedTransforms);
    });

  var updatePublicWatches = function(skip, limit, query, reset) {
    $scope.publicSearchLoading = true;
    $http.get(_.strFormat('/api/public/watches?skip={0}&limit={1}&query={2}', skip, limit, encodeURIComponent(query)))
    .success(function(data) {
      if (reset) $scope.publicWatches = null;

      if (!$scope.publicWatches) {
        $scope.publicWatches = _.pick(data, "watches", "total", "queryHits");
        $scope.publicWatches.watches = _.map($scope.publicWatches.watches, combinedTransforms);
      } else {
        $scope.publicWatches.watches = $scope.publicWatches.watches.concat(_.map(data.watches, combinedTransforms));
      }

      $scope.publicSearchLoading = false;
    });
  };

  updatePublicWatches($scope.currentSkip, publicWatchLimit, '');

  $scope.findMorePublicWatches = function() {
    $scope.currentSkip += publicWatchLimit;
    updatePublicWatches($scope.currentSkip, publicWatchLimit, $scope.publicSearchTerm || '', false);
  };

  $scope.searchPublicWatches = function() {
    $scope.currentSkip = 0;
    updatePublicWatches($scope.currentSkip, publicWatchLimit, $scope.publicSearchTerm || '', true);
  };
}]);
