'use strict';

try { angular.module('sade'); } catch (e) { angular.module('sade', []); }
angular.module('sade').directive('sadeCarousel', function() {
  return {
    restrict: 'E',
    transclude: true,
    template: '<div ng-transclude></div>',
    replace: true,
    link: function(scope, element, attributes) {
      var stepAmount = 2;
      var headerElem = $(element).find('.carouselHeader');
      var carouselElem = $(element).find('.carousel');

      var toggleHeaderActive = function() {
        var activeTypes = _.union($(carouselElem).jcarousel('visible').attr('data-widget-key'));
        $(headerElem).find('li').removeClass('active');
        $(headerElem).find('li').each(function(i, e) {
          for (var k = 0; k < activeTypes.length; k++) {
            if ($(e).attr('data-widget-key') === activeTypes[k]) {
              $(e).addClass('active');
            }
          }
        });
      };

      // FIXME: this is kinda ugly since this 'corrupts' the parent scope, shouldn't do this
      //        in a directive :(
      scope.carouselLeft = function() {
        $(carouselElem).jcarousel('scroll', '-=' + stepAmount);
        toggleHeaderActive();
      };

      scope.carouselRight = function() {
        $(carouselElem).jcarousel('scroll', '+=' + stepAmount);
        toggleHeaderActive();
      };

      scope.goTo = function(key) {
        $(carouselElem).jcarousel('scroll', $(carouselElem).find('[data-widget-key="' + key + '"]'));
        toggleHeaderActive();
      };

      scope.$watch(attributes.data, function(value) {
        if (value) {
          // FIXME: hack, without this timeout the list isn't initialized
          //        and we can't set the ul to proper width. Using scope.$apply()
          //        does enforce the list to populate but in such a case we
          //        get a "digest already in process" error into log. Hope we'll
          //        find a nicer solution for this
          setTimeout(function() {
            var totalWidth = 0;
            $(carouselElem).find('li').each(function(i, e) {
              // FIXME: for some reason jQuery.outerWidth doesn't count the margins (like it should), thus +10
              totalWidth += ($(e).outerWidth() + 10);
            });
            $(carouselElem).find('ul').width(totalWidth);

            $(carouselElem).jcarousel({
              wrap: 'both',
              animation: 'fast'
            });

            toggleHeaderActive();
          }, 50);
        }
      });
    }
  };
});
