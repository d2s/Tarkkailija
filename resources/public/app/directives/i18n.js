'use strict';

var tarkkailija = tarkkailija || {};

try { angular.module('sade'); } catch (e) { angular.module('sade', []); }
angular.module('sade')
.service('i18n', ['$rootScope', '$cookies', function($rootScope, $cookies) {
  var log = tarkkailija.Utils.getLogger('i18n');
  var i18n = tarkkailija.configuration.i18n;

  // used for checking unused localization elements, check via listUnused()
  var readElements = {};

  var getByKey = function() {
    var args = Array.prototype.slice.call(arguments);
    var key = args[0];
    var params = args.slice(1);

    var lang = i18n[$cookies.lang];
    var val = (lang || {})[key];

    if (val) {
      readElements[$cookies.lang] = readElements[$cookies.lang] || {};
      readElements[$cookies.lang][key] = true;
      return _.isEmpty(params) ? val : _.strFormat.apply(_, [val].concat(_.map(params, String)) );
    } else {
      if (key) log.warn("No localization found for key '" + key + "' with lang=" + $cookies.lang);
      return key;
    }
  };

  // use this to locate unused dead localizations
  var listUnused = function() {
    var lang = $cookies.lang;
    var unused = {};

    for (var lang in i18n) {
      unused[lang] = unused[lang] || [];

      for (var key in i18n[lang]) {
        readElements[lang] = readElements[lang] || [];
        if (!readElements[lang][key]) unused[lang].push(key);
      }
    }

    return unused;
  };

  // use this to locate duplicate values for different keys
  var findDuplicates = function() {
    var lang = $cookies.lang;
    var duplicates = {};

    for (var lang in i18n) {
      duplicates[lang] = duplicates[lang] || {};

      for (var key in i18n[lang]) {
        duplicates[lang][i18n[lang][key]] = duplicates[lang][i18n[lang][key]] || [];
        duplicates[lang][i18n[lang][key]].push(key);
      }
    }


    for (var lang in duplicates) {
      duplicates[lang] = _.filter(duplicates[lang], function(e) {
        return e.length > 1;
      });
    }
    return duplicates;
  };

  // global rootscope binding for easy localization calls
  $rootScope.i18n = getByKey;

  // global namespace binding for easy console calls
  tarkkailija.listUnusedLocalizations = listUnused;
  tarkkailija.findDuplicateLocalizations = findDuplicates;

  return {
    getByKey: getByKey,
    listUnused: listUnused,
    findDuplicates: findDuplicates,
    getCurrentLang: function() { return $cookies.lang; }
  };
}])

.directive('sadeI18n', ['i18n', function(i18n) {
  return {
    restrict: 'A',
    link: function(scope, element, attr) {
      $(element).text(i18n.getByKey(attr.sadeI18n));
    }
  }
}]);