'use strict';

try { angular.module('sade'); } catch (e) { angular.module('sade', []); }
angular.module('sade')
.service('FlashMessage', function() {
  var listeners = [];

  var add = function(msg) {
    _.each(listeners, function(l) { l(msg); });
  };

  var registerListener = function(l) {
    listeners.push(l);
  };

  tarkkailija.FlashMessage = {};
  tarkkailija.FlashMessage.add = add;

  return {
    add: add,
    registerListener: registerListener
  };
})

.directive('sadeFlashmessage', ['FlashMessage', function(FlashMessage) {
  return {
    restrict: 'E',
    replace: true,
    templateUrl: '/app/directives/flashmessage.html',
    link: function(scope, element) {
      var FADE_TIMEOUT_MS = 300;
      var DEFAULT_MESSAGE_DELAY_MS = 6000;

      scope.messages = [];
      scope.highestType = 'info';

      var sortOrder = ['info', 'warn', 'error'];

      var findHighestMessageType = function(messages) {
        var current = {type: 'info'};
        for (var i = 0; i < messages.length; i++) {
          // slow comparison but who cares? At extreme worst case there's like 20 of these at the same time...
          if (sortOrder.indexOf(current.type) < sortOrder.indexOf(messages[i].type)) {
            current = messages[i];
          }
        }
        return current.type;
      };

      FlashMessage.registerListener(function(msg) {
        scope.messages.push(msg);
        scope.highestType = findHighestMessageType(scope.messages);
        if (!scope.$$phase) { scope.$apply(); }

        if (scope.messages.length > 0) { $(element).fadeIn(FADE_TIMEOUT_MS); }

        setTimeout(function() {
          scope.messages = scope.messages.slice(1);
          if (scope.messages.length <= 0) {
            $(element).fadeOut(FADE_TIMEOUT_MS, function() {
              scope.$apply();
            });
          } else {
            scope.highestType = findHighestMessageType(scope.messages);
            scope.$apply();
          }
        }, msg.delay || DEFAULT_MESSAGE_DELAY_MS);
      });
    }
  };
}]);
