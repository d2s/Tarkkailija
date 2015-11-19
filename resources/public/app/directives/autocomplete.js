'use strict';

try { angular.module('sade'); } catch (e) { angular.module('sade', []); }
angular.module('sade').directive('sadeAutocomplete', function() {
  return {
    restrict: 'E',
    replace: true,
    transclude: true,
    template: '<input name="autocomplete" type="text" />',
    scope: true,
    link: function(scope, element, attrs) {
      scope.$watch(attrs.source, function(value) {
        element.autocomplete({
          source: value,
          minLength: 3,
          autoFocus: true,
          select: function(event, ui) {
            if (attrs.selectEvent) {
              element.val(scope.$parent[attrs.selectEvent](ui.item));
              event.preventDefault();
            } else {
              scope[attrs.selection] = ui.item.value;
            }
            scope.$apply();
          },
          focus: function(event) {
            event.preventDefault();
          },
          response: function(event, ui) {
            // ui.content is the array that's about to be sent to the response callback.
            if (ui.content.length === 0) {
                var y = {
                  label: "Ei tuloksia haullasi",
                  value: 0
                };
                ui.content.push(y);  
            } else {
            }
          }
        });
      });
    }
  };
});
