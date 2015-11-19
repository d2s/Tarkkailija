'use strict';

angular.module('analytics', []).run(['$rootScope', '$location', '$window', '$routeParams', function($rootScope, $location, $window, $routeParams) {
  var log = tarkkailija.Utils.getLogger('Analytics');

  $window._gaq = $window._gaq || [];

  var convertPathToQueryString = function(path, $routeParams) {
    for (var key in $routeParams) {
      var queryParam = '/' + $routeParams[key];
      path = path.replace(queryParam, '');
    }

    var querystring = decodeURIComponent($.param($routeParams));

    if (querystring === '') { return path; }

    return path + '?' + querystring;
  };

  var track = function() {
    var path = convertPathToQueryString($location.path(), $routeParams);
    $window._gaq.push(['_trackPageview', path]);
  };

  var gaKey = tarkkailija.configuration.gaKey;
  var mode = tarkkailija.configuration.mode;

  if(gaKey) {
    $window._gaq.push(['_setAccount', gaKey]);
    $window._gaq.push(['_trackPageview']);

    var ga = document.createElement('script');
    ga.type = 'text/javascript';
    ga.async = true;
    ga.src = ('https:' === document.location.protocol ? 'https://ssl' : 'http://www') + '.google-analytics.com/ga.js';
    var s = document.getElementsByTagName('script')[0];
    s.parentNode.insertBefore(ga, s);

    $rootScope.$on('$viewContentLoaded', track);
  } else {
    log.info('disabled in '+mode);
  }

}]);
