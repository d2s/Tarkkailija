'use strict';

angular.module('tarkkailija').controller('DebugController', function($rootScope, $scope, $http, $location, Users) {
  var log = tarkkailija.Utils.getLogger('DebugController');

  $scope.autoLogin = function() {
    var userParams = {username: 'foo@bar.com', password: 'password'};
    $http.get('/dev/api/fixture/create-activated-user', {params: userParams}).success(function() {
      log.debug('Created user', userParams.username, userParams.password);
      log.debug('Doing autologin');
      Users.login(userParams);
    });
  };

  $scope.clearDb = function() {
    log.warn('Clearing database');
    $http.get('/dev/api/fixture/clear-db').success(function() {
      log.warn('Cleared database, forwarding to frontpage');
      $location.path('/');
    });
  };

  $scope.todoToggle = true;
  $scope.toggleTodos = function() {
    if ($scope.todoToggle) {
      log.debug('Showing TODO fields in UI');
      $rootScope.todoActive = true;
    } else {
      log.debug('Hiding TODO fields from UI');
      $rootScope.todoActive = false;
    }
  };

  $scope.defaultWatchQuery = function(area, category) {
    $location.url('/search?areas=' + area + '&categories=' + category);
  };

  $scope.forceWatchProcessing = function() {
    $http.get('/dev/api/fixture/force-watch-mail-sending');
  };

  $scope.createRandomUsers = function(amount) {
    $http.get('/dev/api/fixture/users/create-random/' + amount);
  };

  $scope.createRandomWatches = function(amount) {
    $http.get('/dev/api/fixture/watches/create-random/' + amount);
  };
});
