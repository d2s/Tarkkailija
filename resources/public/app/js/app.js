'use strict';

$.ajaxSetup({ cache: false });


angular.module('tarkkailija', ['ui','sade','ngCookies','analytics'])
  .config(['$routeProvider', function($routeProvider) {
    $routeProvider
      .when('/index', {templateUrl: 'views/index.html', controller: 'IndexController'})
      .when('/register', {templateUrl: 'views/register.html', controller: 'UserRegisterController'})
      .when('/user', {templateUrl: 'views/user.html'})
      .when('/search', {templateUrl: 'views/search.html', controller: 'SearchController'})
      .when('/search/:profId', {templateUrl: 'views/search.html', controller: 'SearchController'})
      .when('/similar', {templateUrl: 'views/similar.html'})
      .when('/browse', {templateUrl: 'views/browse.html', controller: 'WatchBrowseController'})
      .when('/info', {templateUrl: 'views/info.html'})
      .when('/info/:subpage', {templateUrl: 'views/info.html'})
      .when('/login/:profId', {templateUrl: 'views/login.html', controller: 'LoginController'})
      .when('/feedback', {templateUrl: 'views/feedback.html', controller: 'FeedbackController'})
      .when('/password/forgot', {templateUrl: 'views/forgot-password.html', controller: 'ForgotPasswordController'})
      .when('/password/change/:key', {templateUrl: 'views/change-password.html', controller: 'PasswordResetController'})
      .when('/unsubscribe', {templateUrl: 'views/unsubscribe.html', controller: 'UnsubscribeController'})
      .when('/personal-info', {templateUrl: 'views/personal-info.html', controller: 'PersonalInfoController'})
      .otherwise({redirectTo: '/index'});
  }])

  .run(['$rootScope', '$location', '$cookies', function($rootScope, $location, $cookies) {    
    $rootScope.isLogged = function() {
      return !_.isEmpty($rootScope.user);
    };

    $rootScope.callWhenVisible = function(elementName, func) {
      var elem = angular.element(elementName);
      var interval = setInterval(function() {
        if (elem.width() > 0) {
          clearInterval(interval);
          func();
        }
      }, 10);
    };

    $rootScope.todoActive = true;

    $rootScope.atFrontPage = function() {
      return $location.path() === '/index';
    };

    // bind config.js stuff into rootScope
    $rootScope.configuration = tarkkailija.configuration;
    
    // expose current language into rootScope
    $rootScope.lang = $cookies.lang;
    
    // initialize footer facebook widget (copy-paste from facebook example)
    var facebook = function() {
      var defineLang = function() {
        return $cookies.lang == 'sv' ? 'sv_SE' : 'fi_FI';
      };
      
      var js, fjs = document.getElementsByTagName('script')[0];
      if (document.getElementById('facebook-jssdk')) return;
      js = document.createElement('script'); js.id = 'facebook-jssdk';
      js.src = "//connect.facebook.net/" + defineLang() + "/all.js#xfbml=1&status=0";
      fjs.parentNode.insertBefore(js, fjs);
    };
    
    facebook();
    
  }])
  
  // initialize HTTP interceptor 401 request holder and request retriggers
  .run(['$rootScope', '$http', function($rootScope, $http) {
    $rootScope.unauthorizedRequests = [];

    $rootScope.$on('tarkkailija:event:loginConfirmed', function() {
      var retry = function(req) {
        $http(req.config).then(function(response) {
          req.deferred.resolve(response);
        });
      };

      _.each($rootScope.unauthorizedRequests, retry);
      $rootScope.unauthorizedRequests = [];
    });
  }])

  // bind the HTTP interceptor to inform rest of the app about unauthorized HTTP access
  .config(['$httpProvider', function($httpProvider) {
    var log = tarkkailija.Utils.getLogger("LoginInterceptor");
    
    var loginInterceptor = ['$rootScope', '$q', function($rootScope, $q) {
      var success = function(response) {
        return response;
      };

      var error = function(response) {
        if (response.status === 401) {
          log.warn("Unauthorized request, deferring response and publishing event");
          var deferred = $q.defer();
          var req = {
            config: response.config,
            deferred: deferred
          };
          $rootScope.unauthorizedRequests.push(req);
          $rootScope.$broadcast('tarkkailija:event:loginRequired');
          return deferred.promise;
        }
        return $q.reject(response);
      };

      return function(promise) {
        return promise.then(success, error);
      };
    }];

    $httpProvider.responseInterceptors.push(loginInterceptor);
  }])

  .factory('Categories', ['$http', '$cookies', function($http, $cookies) {
    var categories = [];
    $http.get('/api/categories').success(function(data) {
      var convertCategoryName = function(c) {
        return _.omit(_.extend(c, {name: c.names[$cookies.lang]}), "names");
      };
      categories = _.map(data, convertCategoryName).sort(function (a, b) { return a.name < b.name; });
    });

    // The whole thing below is done in order to prevent multiple
    // HTTP requests: Categories.find() is called a LOT.
    var pushCategoriesTo = function(callback) {
      if (categories.length > 0) {
        callback(categories);
        return true;
      }
      return false;
    };

    return {
      find: function(callback) {
        if (!pushCategoriesTo(callback)) {
          var interval = setInterval(function() {
            if (pushCategoriesTo(callback)) {
              clearInterval(interval);
            }
          }, 100);
        }
      }
    };
  }])

  .factory('Users', ['$http', '$rootScope', function($http, $rootScope) {

    function login(data, success, error) {
      $http.post('/security/login', data).success(function(data) {
        $rootScope.user = data.user;
        if(success) {
          $rootScope.$broadcast('tarkkailija:event:loginConfirmed');
          success(data);
        }
      }).error(function(data) {
        if(error) {error(data);}
      });
    }

    function loadUser() {
      $http.get('/security/user').success(function(data) {
        $rootScope.user = data.user;
      });
    }
    loadUser();

    return {
      login: login,
      loadUser: loadUser
    };
  }]);