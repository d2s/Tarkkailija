'use strict';

try { angular.module('sade'); } catch (e) { angular.module('sade', []); }

angular.module('sade').directive(['focus', 'blur', 'keyup', 'keydown', 'keypress'].reduce(function (container, name) {
    var directiveName = 'sade' + name[0].toUpperCase() + name.substr(1);

    container[directiveName] = ['$parse', function($parse) {
      return {
        restrict: 'A',
        scope: false,
        link: function(scope, element, attr) {
          var fn = $parse(attr[directiveName]);
          element.bind(name, function (event) {
            scope.$apply(function() {
              fn(scope, {$event: event});
            });
          });
        }
      };
    }];

    return container;
  }, {}));


angular.module('sade').directive('sadeWindowScale', function() {
  return {
    restrict: 'A',
    link: function(scope, element, attr) {
      var options = scope.$eval(attr.sadeWindowScale);

      var scaleElement = function() {
        if (options.horizontalMargin) { $(element).width($(window).width() - options.horizontalMargin); }
        if (options.verticalMargin) { $(element).height($(window).height() - options.verticalMargin); }
      };

      $(window).resize(scaleElement);
      scaleElement();
    }
  };
});
