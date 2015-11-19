'use strict';

try { angular.module('sade'); } catch (e) { angular.module('sade', []); }

(function() {

  var FADE_MS = 1000;

  var doFade = function(model, order, element, evalResult, fadeIn) {
    if (model && !model.sadeFadeQueue) {
      model.sadeFadeQueue = [];
    }

    var processQueue = function() {
      if (model.sadeFadeQueue[0]) {
        model.sadeFadeQueueProcessing = true;
        model.sadeFadeQueue.splice(0, 1)[0]();
      } else {
        model.sadeFadeQueueProcessing = false;
      }
    };

    var addToQueue = function(callback, order) {
      if (model && model.sadeFadeQueue) {
        model.sadeFadeQueue[parseInt(order, 10) - 1] = callback;
      } else {
        callback();
      }
      console.log(model.sadeFadeQueue);
    };

    if (evalResult) {
      addToQueue(function() {
        element[fadeIn ? 'fadeIn' : 'fadeOut'](FADE_MS, processQueue);
      }, order || '1');
    } else {
      addToQueue(function() {
        element[fadeIn ? 'fadeOut' : 'fadeIn'](FADE_MS, processQueue);
      }, order || '1');
    }

    if (!model.sadeFadeQueueProcessing) {
      console.log('initializing processing:', (fadeIn ? 'fadeIn' : 'fadeOut'));
      processQueue();
    }
  };

  angular.module('sade')
  .directive('sadeShowFade', ['$timeout', function($timeout) {
    return {
      restrict: 'A',
      priority: 1,
      name: 'sadeFade',
      require: ['^ngModel', '?sadeFadeOrder'],
      scope: {
        ngModel: '&ngModel',
        sadeFadeOrder: '='
      },
      link: function(scope, element, attr) {
        console.log(scope.$parent);
        if (scope.$eval(attr.sadeShowFade)) {
          element.show();
        } else {
          element.hide();
        }

        scope.$watch(attr.sadeShowFade, function(predicateResult) {
          $timeout(function() {
            doFade(scope.$parent, scope.sadeFadeOrder, element, predicateResult, true);
          });
        });
      }
    };
  }])
  .directive('sadeHideFade', ['$timeout', function($timeout) {
    return {
      restrict: 'A',
      priority: 2,
      name: 'sadeFade',
      require: ['^ngModel', '?sadeFadeOrder'],
      scope: {
        ngModel: '&ngModel',
        sadeFadeOrder: '='
      },
      link: function(scope, element, attr) {
        console.log(scope.$parent);
        if (scope.$eval(attr.sadeHideFade)) {
          element.hide();
        } else {
          element.show();
        }

        scope.$watch(attr.sadeHideFade, function(predicateResult) {
          $timeout(function() {
            doFade(scope.$parent, scope.sadeFadeOrder, element, predicateResult, false);
          });
        });

      }
    };
  }]);

})();
