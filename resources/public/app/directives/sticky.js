'use strict';

try { angular.module('sade'); } catch (e) { angular.module('sade', []); }
angular.module('sade').directive('sadeSticky', ['$timeout', function($timeout) {
  var log = tarkkailija.Utils.getLogger('sadeSticky');
  
  return {
    restrict: 'A',
    link: function(scope, element, attributes) {
      var defaultOpts = {
        widthMargin: 0,
        offset: 0
      };
      
      var parseOpts = function(opts) {
        if (typeof(opts) === 'string' || typeof(opts) == 'number') {
          // just string or number, interpret as widthMargin
          return _.extend(defaultOpts, {widthMargin: parseInt(opts)});
        } else if (typeof(opts) == 'object') {
          return _.extend(defaultOpts, opts);
        }
        return defaultOpts;
      };
      
      var wrapOffset = function(opts) {
        var offsetFunc = function() {
          if (typeof(opts.offset) === 'function') {
            return opts.offset(this);
          }
          return opts.offset;
        };
        
        return _.extend(_.deepClone(opts), {offset: offsetFunc});
      };
      
      $timeout(function() {
        var opts = wrapOffset(parseOpts(scope.$eval(attributes.sadeSticky)));
        
        $(element).waypoint('sticky', _.pick(opts, 'offset'));
        
        if (opts.context) {
          var $ctx = $('#' + opts.context);
          
          var off = typeof(opts.offset) === 'function' ? opts.offset() : opts.offset;
          
          $ctx.waypoint(_.extend($.fn.waypoint.defaults, {
            handler: function(dir) {
              if (dir === 'down') {
                var curOffset = $(element).offset().top;
                
                $(element)
                  .css({position: 'absolute'})
                  .css({top: (function() {
                    return curOffset - $(element).offsetParent().offset().top - $(element).outerHeight() - 110; // FIXME: hardcoding
                  }())});
              } else if (dir === 'up') {
                $(element).css({
                  position: '',
                  top: ''
                });
              }
            },
            offset: off + $(element).height() + 160 // FIXME: hardcoding
          }));
        }
        
        $(element).width($(element).width() + opts.widthMargin); // enforce content width
        
        $.waypoints('refresh'); // ensure waypoints are correctly calculated
      });
    }
  };
}]);
