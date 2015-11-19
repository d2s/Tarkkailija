'use strict';

try { angular.module('sade'); } catch (e) { angular.module('sade', []); }
angular.module('sade').directive('sadeMisclick', ['$timeout', function($timeout) {
  return {
    restrict: 'A',
    link: function(scope, element, attributes) {
      $('body').click(function(e) {
        if ($(e.target).closest(element).length == 0) {
          scope.$apply(attributes.sadeMisclick);
        }
      });
    }
  }
}]);
