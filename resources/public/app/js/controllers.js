'use strict';

// needed to wrap JSONP handlers
var tarkkailija = tarkkailija || {};
tarkkailija.jsonp = tarkkailija.jsonp || {};

// FIXME: this is *HORRIBLE* in all possible ways!
angular.module('tarkkailija').directive('share', function() {
  /* global addthis */
  return {
    restrict: 'C',
    link: function(scope, element) {
      var icon = $(element);
      var toolbox = icon.parents('.addthis_toolbox')[0];
      var shares = $(toolbox).find('.shares');
      var initialized = false;
      window.E = icon;
      function toggle() {
        shares.fadeToggle(200);
        if(!initialized && typeof addthis !== 'undefined' && addthis !== null) {
          addthis.toolbox(toolbox);
        }
        initialized = true;

        // Let's continue on the way of horrible hacks since this is one huge hack itself :P
        if (shares.is(':visible')) {
          var mask = $('<div style="position: absolute; left: 0; top: 0; right: 0; bottom: 0; z-index: 9;"></div>').click(function(e) {
            shares.fadeToggle(200);
            $(this).remove();
          });

          $('body').append(mask);
        }
      }
      icon.bind('click', toggle);
    }
  };
});

angular.module('tarkkailija').controller('IndexController',
    ['$rootScope', '$scope', '$location', '$http', 'i18n',
     function($rootScope, $scope, $location, $http, i18n) {

  $scope.feeds = $scope.feeds || {};

  var countMatching = function(list, pred) {
    return _.reduce(list, function(sum, elem) {
      return sum + (pred(elem) ? 1 : 0);
    }, 0);
  };

  $scope.feeds.tabs = [
    {type: 'PUBLIC', name: i18n.getByKey('watch.own'), show: function() { return true; }},
    {type: 'PRIVATE', name: i18n.getByKey('watch.public'), show: function() {
      return $rootScope.isLogged() && countMatching($scope.feeds.watches, function(e) {
        return e.category === 'PRIVATE';
      }) > 0;
    }}
  ];

  $scope.feeds.selected = 'PUBLIC';
  $scope.feeds.select = function(tab) {
    $scope.feeds.selected = tab.type;
  };

  $scope.shouldShowTabs = function() {
    return countMatching($scope.feeds.tabs, function(tab) { return tab.show(); }) > 1;
  };

  $scope.feeds.isActive = function(tab) {
    return $scope.feeds.selected === tab.type;
  };

  $scope.showWatch = function(watch) {
    $location.path('/search/' + watch._id);
  };

  tarkkailija.jsonp.leikiWidget = function(data) {
    var defaultImage = function(e) {
      return '/api/map-image/' + encodeURIComponent(e.id);
    };

    var findFeeds = function(data, name) {
      for (var key in data.tabs) {
        if (data.tabs[key].headline === name) {
          return data.tabs[key].items;
        }
      }
      return [];
    };

    var addKey = function(key) {
      return function(e) {
        e.key = key;
        return e;
      };
    };

    var convertLink = function(e) {
      return _.extend(e, {url: '/api/show-article?id=' + encodeURIComponent(e.id)});
    };

    var convertEmptyImages = function(e) {
      return _.extend(e, {image: _.strEndsWith(e.image, 'focus/widgets/common/img/empty.gif') ? defaultImage(e) : e.image});
    };

    var excludeEmptyCategories = function(e) {
      return findFeeds(data, e.key).length > 0;
    };

    var buildMapOperations = function(key) {
      return _.compose(addKey(key), convertLink, convertEmptyImages);
    };

    if (data && data.tabs) {
      $scope.widget = $scope.widget || {};

      $scope.widget.titles = _.filter([
        {name: i18n.getByKey('widget.authorities.personal'), key: 'VIRANOMAISET_HENKKOHT'},
        {name: i18n.getByKey('widget.authorities.popular'), key: 'VIRANOMAISET_PINNALLA'},
        {name: i18n.getByKey('widget.media.personal'), key: 'MEDIA_HENKKOHT'},
        {name: i18n.getByKey('widget.media.popular'), key: 'MEDIA_PINNALLA'}
      ], excludeEmptyCategories);

      $scope.widget.items = _.union(
        _.map(findFeeds(data, 'VIRANOMAISET_HENKKOHT'), buildMapOperations('VIRANOMAISET_HENKKOHT')),
        _.map(findFeeds(data, 'VIRANOMAISET_PINNALLA'), buildMapOperations('VIRANOMAISET_PINNALLA')),
        _.map(findFeeds(data, 'MEDIA_HENKKOHT'), buildMapOperations('MEDIA_HENKKOHT')),
        _.map(findFeeds(data, 'MEDIA_PINNALLA'), buildMapOperations('MEDIA_PINNALLA'))
      );
    }
  };

  // TODO: this address should come from configurations, not hard coded to javascript
  $http.jsonp(tarkkailija.configuration.leiki + '/focus/mwidget?wname=tarkkailija2&cid=http%3A%2F%2Fwww.tarkkailija.fi&callback=tarkkailija.jsonp.leikiWidget&_timestamp=' + (new Date()).getTime());

  $scope.feeds.watches = $scope.feeds.watches || [];

  if ($rootScope.isLogged()) {
    $http.get('/api/watches-with-articles/5').success(function(data) {
      $scope.feeds.watches = _.union($scope.feeds.watches, _.map(data.watches, function(e) {
        return _.extend(e, {category: 'PRIVATE'});
      }));
    });
  }

}]);

angular.module('tarkkailija').controller('UserRegisterController', ['$scope', '$http', function($scope, $http) {
  $scope.save = function() {
    $http.post('/api/users',$scope.data).success(function() {
      $scope.sent = true;
      $scope.emailsent = $scope.data.email;
      $scope.data = null;
      $scope.registerForm.$setPristine();
    });
  };

  $scope.correctPassword = function(value) {
    return value && $scope.data.password === value;
  };

  $scope.uniqueEmail = function() {
    if ($scope.data.email) {
      $http.get('/api/exists', {params: {email: $scope.data.email}}).success(function(data) {
        $scope.emailExists = (data === 'true');
      });
    }
  };
}]);

angular.module('tarkkailija').controller('LoginController',
    ['$rootScope', '$scope', '$http', '$location', '$route', 'Users',
     function($rootScope, $scope, $http, $location, $route, Users) {
  $scope.login = function() {
    $scope.error = '';  //reset

    Users.login(
      $scope.data,
      function() {
        $scope.data = '';
        $route.reload();
      },
      function() {
        $scope.errorWrongPassword = true;
      });
  };

  $rootScope.$on('tarkkailija:event:loginRequired', function() {
    $scope.errorLoginRequired = true;
  });
}]);

angular.module('tarkkailija').controller('ForgotPasswordController',
    ['$scope', '$http', 'FlashMessage',
     function($scope, $http, FlashMessage) {

  var log = tarkkailija.Utils.getLogger('ForgotPasswordController');

  $scope.sendResetEmail = function() {
    $http.post('/api/password-reset-email', $scope.data)
      .success(function() {
        $scope.sent = true;
        $scope.emailsent = $scope.data.email;
      })
      .error(function(data, status) {
        if (status === 404) {
          log.warn('Password reset email couldn\'t be sent to non-existing email', data, status);
          FlashMessage.add({type: 'warn', content: 'Annetulla sähköpostiosoitteella ei löytynyt käyttäjätunnusta. Tarkista syöttämäsi osoite.'});
        } else {
          log.error('Error occured in email password reset', data, status);
          FlashMessage.add({type: 'error', content: 'Salasanan vaihto epäonnistui, yritä hetken kuluttua uudelleen'});
        }
      });
  };
}]);

angular.module('tarkkailija').controller('PasswordResetController',
    ['$scope', '$http', '$location', 'FlashMessage',
     function($scope, $http, $location, FlashMessage) {

  var log = tarkkailija.Utils.getLogger('PasswordResetController');

  $scope.changePassword = function() {
    var parseKey = function() {
      var parts = $location.path().split('/');
      return parts[parts.length - 1];
    };

    $http.post('/security/reset-password/' + parseKey(), $scope.data)
      .success(function() {
        $scope.changed = true;
      })
      .error(function(data, status) {
        log.error('Error occured in user password reset', data, status);
        FlashMessage.add({type: 'error', content: 'Salasanan vaihto epäonnistui, yritä hetken kuluttua uudelleen'});
      });
  };

  $scope.correctPassword = function(value) {
    return value && $scope.data.password === value;
  };
}]);

angular.module('tarkkailija').controller('FeedbackController',
    ['$scope', '$http',
     function($scope, $http) {

  $scope.data = {};
  $scope.data.feedbackType = 'bug';

  $scope.sendFeedback = function() {
    $http.post('/api/feedback', $scope.data).success(function() {
      $scope.sent = true;
      $scope.data = null;
      $scope.feedbackForm.$setPristine();
    });
  };
}]);

angular.module('tarkkailija').controller('UnsubscribeController',
    ['$scope', '$http', '$location', 'FlashMessage',
     function($scope, $http, $location, FlashMessage) {

  var log = tarkkailija.Utils.getLogger('UnsubscribeController');
  var params = $location.search();

  $scope.email = params.email;

  $scope.cancel = function() {
    log.info(_.strFormat('Unsubscribe for watch {0} canceled', params.watchId));
    $location.path('/');
  };

  $scope.accept = function() {
    $http['delete']('/api/subscribes/remove-with-token', {params: params})
      .success(function() {
        $scope.unsubscribeSuccessfull = true;
      })
      .error(function(data, status) {
        if (status === 403) {
          FlashMessage.add({type: 'warn', content: 'Ei oikeuksia poistaa vahdilta tilausta'});
          log.warn('Security error while trying to unsubscribe for watch', params.watchId);
        } else {
          FlashMessage.add({type: 'error', content: 'Tapahtui virhe vahdin tilauksen poistossa, yritä hetken kuluttua uudelleen'});
          log.error('Error occured while performing unsubscribe for watch', params.watchId);
        }
      });
  };
}]);

angular.module('tarkkailija').controller('InfoController', ['$scope', '$location', function($scope, $location) {
  var subpage = $location.path().substr('/info/'.length);
  $scope.infoPage = subpage ? subpage : 'help';
}]);

angular.module('tarkkailija').controller('LangBasedSubResourceController', ['$scope', '$cookies', function($scope, $cookies) {
  $scope.lang = $cookies.lang;
}]);

angular.module('tarkkailija').controller('SourcesController', ['$scope', '$http', function($scope, $http) {
  $http.get('/api/sources').success(function(data) {
    $scope.media = data.media;
    $scope.authorities = data.authorities;
  });
}]);

angular.module('tarkkailija').controller('LanguageController', ['$scope', '$http', function($scope, $http) {
  $scope.changeLocale = function(locale) {
    $http.get('/?lang=' + locale)
     .success(function() {
       // force refresh of the same page
       window.location.reload();
     });
  };

  $scope.hasMultipleLanguageChoices = function() {
    return tarkkailija.configuration.langs.length > 1;
  };

  $scope.hasLanguage = function(lang) {
    return tarkkailija.configuration.langs.indexOf(lang) > -1;
  };
}]);
