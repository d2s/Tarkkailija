'use strict';

try { angular.module('sade'); } catch (e) { angular.module('sade', []); }
angular.module('sade').directive('sadeDialog', ['$timeout', function($timeout) {
  return {
    restrict: 'A',
    require: 'ngModel',
    link: function(scope, element, attributes, model) {
      element.addClass('window');

      scope.$watch(attributes.ngModel, function(value) {
        if (value) {
          element.before('<div class="mask black transparent"></div>');
          element.prev('.mask').addBack().fadeIn(150);
        } else {
          element.prev('.mask').addBack().fadeOut(150, function() {
            element.prev('.mask').remove();
          });
        }
      });

      element.on('shown', function() {
        $timeout(function() {
          model.$setViewValue(true);
        });
      });

      element.on('hidden', function() {
        $timeout(function() {
          model.$setViewValue(false);
        });
      });

      $(document).keyup(function(e) {
        if (e.keyCode === 27) {
          $timeout(function() {
            model.$setViewValue(false);
          });
        }
      });

      if (attributes.sadeDialog === 'resize') {
        var resizeDialog = function() {
          var newWidth = $(window).width() - 200;
          var newHeight = $(window).height() - 100;

          $(element)
            .width(newWidth)
            .height(newHeight)
            .css('left', 270)
            .css('top', 50);

          scope.$broadcast('dialogResize', {width: newWidth, height: newHeight});
        };

        $(window).resize(resizeDialog);
        resizeDialog();
      }
    }
  };
}]);
