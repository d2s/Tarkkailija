'use strict';

(function(angular) {
  var module = angular.module('tarkkailija');

  var log = tarkkailija.Utils.getLogger('ReportingController');
  var dates = tarkkailija.dates;

  // FIXME: graph logic should be in a directive
  // FIXME: this file got out of hand in other ways too, needs desperate refactoring (directive!!)
  module.controller('ReportingController',
      ['$scope', '$http', '$filter', '$timeout', 'i18n',
       function($scope, $http, $filter, $timeout, i18n) {

    var monthNames = _.map(i18n.getByKey('general.months.short').split(','), function(s) { return s.trim(); });
    var dayNames = _.map(i18n.getByKey('general.days.short').split(','), function(s) { return s.trim(); });

    var generalAxisOptions = {
        show: true,
        font: 'Dosis, sans-serif',
        color: 'rgb(0,0,0)',
        tickColor: 'rgb(220,220,220)'
      };

    var generalFlotOptions = {
        legend: {
          show: false
        },
        grid: {
          hoverable: true
        }
      };

    var yaxisOptions = {
      minTickSize: 1
    };

    var buildOptions = function(/*arguments*/) {
      var args = Array.prototype.slice.call(arguments);

      var result = _.extend(_.deepClone(generalFlotOptions), {
        xaxis: _.deepClone(generalAxisOptions),
        yaxis: _.extend(_.deepClone(generalAxisOptions), yaxisOptions)
      });

      for (var i = 0; i < args.length; i++) {
        // apply xaxis configs
        if (_.keys(args[i]).indexOf('xaxis') > -1) {
          _.extend(result.xaxis, args[i].xaxis);
        }

        // apply yaxis configs
        if (_.keys(args[i]).indexOf('yaxis') > -1) {
          _.extend(result.yaxis, args[i].yaxis);
        }

        // some other value, just dump it in
        _.extend(result, args[i]);
      }

      return result;
    };

    var pickFlotDateFormatFor = function(dayAmount) {
      if (dayAmount <= 31) return '%d.%m.%Y';
      // 366 == take leap years into account
      if (dayAmount <= 366) return '%b %Y';
      return '%Y';
    };

    var defineOneTimeUnitForDays = function(dayAmount) {
      if (dayAmount <= 31) return 24*60*60*1000; // 1d
      if (dayAmount <= 365) return 24*60*60*1000*7; // 1w
      if (dayAmount <= 700) return 24*60*60*1000*30; // 1m
      else return 24*60*60*1000*365; // 1y
    };

    var rangeDaysToString = function(days) {
      if (days <= 60) return "day";
      if (days <= 450) return "month";
      return "year";
    };

    // Drawing graphs

    var createTimeBasedXAxis = function(range) {
      var rangeDays = dates.daysBetween(range.start, range.end);
      var timeUnit = defineOneTimeUnitForDays(rangeDays)

      return {xaxis: {
        min: range.start,
        max: range.end + timeUnit,
        mode: 'time',
        minTickSize: [1, rangeDaysToString(rangeDays)],
        timeformat: pickFlotDateFormatFor(rangeDays),
        monthNames: monthNames,
        dayNames: dayNames
      }};
    };

    var baseHoverHandler = function(dx, dy, contentProvider) {
      return function(event, pos, item) {
        $('#graph-tooltip').remove();
        if (item) {
          $('<div id="graph-tooltip"></div>').css({
            top: item.pageY + dy,
            left: item.pageX + dx,
            display: 'none'
          }).html(contentProvider(item.datapoint))
            .appendTo('body')
            .show();
        }
      };
    };

    var hoverHandler = function(dx, dy, contentIndex) {
      return baseHoverHandler(dx, dy, function(datapoint) { return datapoint[contentIndex]; });
    };

    var timeBasedHoverHandler = function(dx, dy, i18nkey, format) {
      return baseHoverHandler(dx, dy, function(datapoint) {
        return '<b>' + $.plot.formatDate(new Date(datapoint[0]), format, monthNames, dayNames)
               + '</b><br/>' + i18n.getByKey(i18nkey, datapoint[1]);
      });
    };

    var loadIndicatorTimeouts = {};
    var ajaxLoadShow = function(name) {
      loadIndicatorTimeouts[name] = $timeout(function() {
        $scope[name] = true;
      }, 100);
    };

    var ajaxLoadHide = function(name) {
      if (loadIndicatorTimeouts[name]) {
        $timeout.cancel(loadIndicatorTimeouts[name]);
        loadIndicatorTimeouts[name] = null;
      }
      $scope[name] = false;
      if(!$scope.$$phase) $scope.$apply();
    };

    // FIXME: this is getting *super* ugly... :( NEEDS REFACTORING!
    var latestResultAmounts = {};

    var createDrawUserAmountGraphHandler = function(range) {
      return function(data) {
        ajaxLoadHide("queryInProgressUsersRegistered");

        latestResultAmounts['usersRegistered'] = data.length;

        // this is needed in order to give angular a chance to update visibilities before we apply
        // flot - otherwise flot screws up dimensions since the container element isn't visible before
        // it tries to do it's magic.
        // ...and yes, FIXME :(
        var d = data;
        $timeout(function() {
          if (d.length == 0) {
            $('#registered-users-report').empty();
            return;
          }

          var rangeDays = dates.daysBetween(range.start, range.end);
          var timeUnit = defineOneTimeUnitForDays(rangeDays)

          var xAxis = createTimeBasedXAxis(range);
          xAxis.xaxis.tickColor = 'rgba(220,220,220,0)';

          var data = [{bars: {show: true, align: 'center', barWidth: timeUnit}, data: d}];
          var options = {yaxis: {tickDecimals: 0}};

          $.plot($('#registered-users-report'), data, buildOptions(xAxis, options));
          $('#registered-users-report').bind('plothover', timeBasedHoverHandler(0, -55, 'general.users.new', pickFlotDateFormatFor(rangeDays)));
        }, 0);

      }
    };

    var createDrawUserCumulativeAmountGraphHandler = function(range) {
      return function(data) {
        ajaxLoadHide("queryInProgressUsersCumulative");

        latestResultAmounts['usersCumulative'] = data.length;

        var d = data;
        $timeout(function() {
          if (d.length == 0) {
            $('#cumulative-users-report').empty();
            return;
          }

          var rangeDays = dates.daysBetween(range.start, range.end);
          var xAxis = createTimeBasedXAxis(range);

          var data = [{data: d}];
          var options = {
            series: {lines: {show: true}, points: {show: true}},
            yaxis: {tickDecimals: 0}
          };

          $.plot($('#cumulative-users-report'), data, buildOptions(xAxis, options));
          $('#cumulative-users-report').bind('plothover', timeBasedHoverHandler(5, 5, 'general.users', pickFlotDateFormatFor(rangeDays)));
        }, 0);
      }
    };


    var createPopularCategoriesGraphHandler = function(range) {
      return function (data) {
        ajaxLoadHide("queryInProgressCategoriesPopular");

        latestResultAmounts['categoriesPopular'] = data.length;

        var d = data;
        $timeout(function() {
          if (d.length == 0) {
            $('#popular-categories-report').empty();
            return;
          }

          var yAxis = {yaxis: {
            tickColor: 'rgba(220,220,220,0)',
            ticks: _.map(d, function(e, i) { return [i, e.name]; })
          }};

          var graphData = [{bars: {show: true, align: 'center', horizontal: true, barWidth: 0.6},
            data: _.map(d, function(e, i) {
              return [e.weight, i];
            })}];

          $('#popular-categories-report').height(d.length * 40);

          $.plot($('#popular-categories-report'), graphData, buildOptions(yAxis))
          $('#popular-categories-report').bind('plothover', hoverHandler(0, -15, 0));
        }, 0);
      };
    };

    // Graph data querying

    // used to get a wider set of data, needed to show continuous line graphs
    var expandRange = function(range) {
      var rangeDays = dates.daysBetween(range.start, range.end);
      var timeUnit = defineOneTimeUnitForDays(rangeDays);
      return {start: range.start - 2*timeUnit, end: range.end};
    };

    var queryGraphData = function(opts) {
      $http({
        url: opts.url,
        method: 'GET',
        params: opts.lang ? _.extend(opts.range, {lang: opts.lang}) : opts.range
      }).success(opts.success)
        .error(function(msg, status) {
          // TODO: flashmessage
          log.error('Error while fetching report data:\n    ', msg, '\n    ', 'Status code:', status);
        });
    };

    var queryUsersGraphWithRange = function(range) {
      ajaxLoadShow("queryInProgressUsersRegistered", $scope.queryInProgressUsersRegistered);
      queryGraphData({range: range, url: '/api/reports/users/by-creation-date', success: createDrawUserAmountGraphHandler(range)});
    };

    var queryUsersCumulativeGraphWithRange = function(range) {
      ajaxLoadShow("queryInProgressUsersCumulative", $scope.queryInProgressUsersCumulative);
      queryGraphData({range: range, url: '/api/reports/users/cumulative-by-creation-date', success: createDrawUserCumulativeAmountGraphHandler(range)});
    };

    var queryCategoriesPopularWithRange = function(range) {
      ajaxLoadShow("queryInProgressCategoriesPopular", $scope.queryInProgressCategoriesPopular);
      queryGraphData({range: range, url: '/api/reports/categories/popular-weighted', lang: i18n.getCurrentLang(), success: createPopularCategoriesGraphHandler(range)});
    };

    // Angular bindings

    $scope.rangeUsersRegistered = '1m'; // default one month
    $scope.rangeUsersCumulative = '1m'; // default one month
    $scope.rangePopularCategories = '1m'; // default one month

    var opts = {
      '5y': dates.fromNow(dates.minusYears, 5),
      '1y': dates.fromNow(dates.minusYears, 1),
      '1m': dates.fromNow(dates.minusMonths, 1),
      '1w': dates.fromNow(dates.minusDays, 7)
    };

    var dateMillisWrap = function(millis, func) {
      return func(new Date(millis)).getTime();
    };

    var normalizeWith = function(rangeObj, func) {
      return {start: dateMillisWrap(rangeObj.start, func), end: dateMillisWrap(rangeObj.end, func)};
    };

    var normalizers = {
      '5y': function(r) { return normalizeWith(r, dates.ceilYear); },
      '1y': function(r) { return normalizeWith(r, dates.ceilMonth); },
      '1m': function(r) { return normalizeWith(r, dates.ceilDay); },
      '1w': function(r) { return normalizeWith(r, dates.ceilDay); }
    };

    var buildRange = function(range) {
      var now = new Date().getTime();

      return normalizers[range]({start: opts[range], end: now});
    };


    // angular change handlers
    $scope.selectTimeRangeUsersRegistered = function() {
      queryUsersGraphWithRange(buildRange($scope.rangeUsersRegistered));
    };

    $scope.selectTimeRangeUsersCumulative = function() {
      queryUsersCumulativeGraphWithRange(buildRange($scope.rangeUsersCumulative));
    };

    $scope.selectTimeRangePopularCategories = function() {
      queryCategoriesPopularWithRange(buildRange($scope.rangePopularCategories));
    };


    // first time graph initialization calls
    queryUsersGraphWithRange(buildRange($scope.rangeUsersRegistered));
    queryUsersCumulativeGraphWithRange(buildRange($scope.rangeUsersCumulative));

    queryCategoriesPopularWithRange(buildRange($scope.rangePopularCategories));


    $scope.hasResults = function(type) {
      return latestResultAmounts[type] && latestResultAmounts[type] > 0;
    };
  }]);

}(angular));