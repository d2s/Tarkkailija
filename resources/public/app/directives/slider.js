'use strict';

try { angular.module('sade'); } catch (e) { angular.module('sade', []); }
angular.module('sade').directive('sadeSlider', function() {
  return {
    restrict: 'A',
    scope: '=',
    link: function(scope, element, attributes) {
      var params = scope.$eval(attributes.sadeSlider);
      $(element).slider({
        range: 'min',
        animate: 'fast',
        slide: function(event, ui) {
          scope.$apply(function() {
            scope[params.model] = scope[params.values][ui.value-1];
          });

          var amount = ((ui.value - 1) / scope[params.values].length) * 100;
          if (amount > 100) {
            amount = 100;
            ui.value = scope[params.values].length - 1;
          }
          $(element).find('.ui-slider-range').css('width', (amount + '%'));
          $(element).find('.ui-slider-handle').css('left', (amount + '%'));
          return false;
        }
      });
    }
  };
});
