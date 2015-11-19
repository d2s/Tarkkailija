'use strict';

try { angular.module('sade'); } catch (e) { angular.module('sade', []); }
angular.module('sade').directive('placeholder', ['$timeout', function($timeout) {
  return {
    restrict: 'A',
    link: function(scope, element) {
      // timeout in order to ensure all angular expressions are evaluated before this is ran
      $timeout(function() {
        // HTML 5 placeholder fix for IE version <10
        $(element).placeholder();
      }, 0);
    }
  };
}]);
