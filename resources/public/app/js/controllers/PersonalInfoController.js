'use strict';

angular.module('tarkkailija').controller('PersonalInfoController', ['$rootScope', '$scope', '$location', '$http', 'FlashMessage', 'i18n',
                                                                    function($rootScope, $scope, $location, $http, FlashMessage, i18n) {
  var log = tarkkailija.Utils.getLogger('PersonalInfoController');

  if (!$rootScope.isLogged()) {
    $location.path('/');
  }

  $scope.email = $rootScope.user.email;
  $scope.subscriptions = [];

  var updateSubscribes = function() {
    $http.get('/api/subscribes')
    .success(function(data) {
      $scope.subscriptions = _.map(data, function(e) {
        return _.extend(e, {lang: i18n.getByKey('general.' + e.lang)});
      });
    });
  };
  updateSubscribes();

  $scope.unsubscribe = function(subscription) {
    $http['delete']('/api/subscribes/' + subscription.id).success(updateSubscribes);
  };

  $scope.saveNewPassword = function() {
    $http.post('/api/change-password', {currentpwd: $scope.data.password, newpwd: $scope.data.newPassword})
      .success(function() {
        log.info('Password changed successfully');
        FlashMessage.add({type: 'info', content: i18n.getByKey('flash.password.change.successful')});
        $scope.data = null;
        $scope.changePasswordForm.$setPristine();
      })
      .error(function(data, status) {
        if (status === 403 && data.text === 'error.invalid-current-password') {
          $scope.changePasswordForm.$error.invalidPassword = true;
        } else {
          log.error('Error while changing password', data);
          FlashMessage.add({type: 'error', content: i18n.getByKey('flash.password.change.error')});
        }
      });
  };

  $scope.correctPassword = function(value) {
    return value && $scope.data.newPassword === value;
  };
}]);
