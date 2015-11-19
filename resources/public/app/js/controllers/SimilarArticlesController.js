'use strict';

(function(angular) {
  var tarkkailijaModule = angular.module('tarkkailija');
  var dates = tarkkailija.dates;
  var log = tarkkailija.Utils.getLogger('SimilarArticlesController');
  
  tarkkailijaModule.controller('SimilarArticlesController', 
      ['$scope', '$location', '$routeParams', '$http', 'i18n', 'WizardState', 'FlashMessage',
       function($scope, $location, $routeParams, $http, i18n, WizardState, FlashMessage) {
      
    //
    // Local data
    //
    
    var log = tarkkailija.Utils.getLogger('SimilarArticlesController');
    
    var monthNames = _.map(i18n.getByKey('general.months.short').split(','), function(s) { return s.trim(); });
    var dayNames = _.map(i18n.getByKey('general.days.short').split(','), function(s) { return s.trim(); });
        
    //
    // First load initializations
    //
    
    if (!WizardState.isEmpty()) {
      $scope.showBackButton = true;
    }
    
    $scope.totalresults = 0;
    $scope.articles = [];
    $scope.selectedArticleLoading = true;
    $scope.timeRangeValues = [{label: i18n.getByKey('timerange.everything'), value: 0},
                              {label: i18n.getByKey('timerange.fiveyears'), value: dates.fromNow(dates.minusYears, 5)},
                              {label: i18n.getByKey('timerange.year'), value: dates.fromNow(dates.minusYears, 1)},
                              {label: i18n.getByKey('timerange.month'), value: dates.fromNow(dates.minusMonths, 1)},
                              {label: i18n.getByKey('timerange.week'), value: dates.fromNow(dates.minusDays, 7)}];
    $scope.timeRange = $scope.timeRangeValues[2].value; // default one year
    
    //
    // Local functions
    //
    
    var pickRange = function(days) {
      if (days <= 31) return {format: '%d.%m.%Y', floorFunc: dates.floorDay};
      if (days <= 356) return {format: '%b %Y', floorFunc: dates.floorMonth};
      return {format: '%Y', floorFunc: dates.floorYear}; 
    };
    
    var createRange = function(articles) {
      var oldest = new Date(_.last(articles).date).getTime();
      var latest = new Date(_.first(articles).date).getTime();
      return pickRange(dates.daysBetween(oldest, latest));
    };
    
    var shortenStringProperty = function(prop, length) {
      return function(article) {
        var shorten = function(val) {
          return !val || val.length <= length ? val : val.substr(0, length-3) + "...";
        };
        
        var o = new Object();
        o[prop] = shorten(article[prop]);
        return _.extend(article, o);
      };
    };
    
    var shortenDescription = function(length) {
      return shortenStringProperty('description', length);
    }
    
    var shortenHeadline = function(length) {
      return shortenStringProperty('headline', length);
    };
    
    var urlEncodeId = function(article) {
      return _.extend(article, {id: encodeURIComponent(article.id)});
    };
    
    var splitByRanges = function(articles) {
      if (!articles || articles.length === 0) return [];
      
      var range = createRange(articles);
      
      var toFlooredDateKeyObj = function(e) {
        return {date: range.floorFunc(new Date(e.date)).getTime(), article: e};
      };

      return _.chain(articles)
              .map(shortenDescription(200))
              .map(urlEncodeId)
              .map(toFlooredDateKeyObj)
              .groupBy('date')
              .pairs()
              .map(function(e) { 
                var o = new Object(); 
                o[e[0]] = _.map(e[1], function(d) { return d.article; }); 
                return o; 
              })
              .sortBy(function(e) { return _.first(_.keys(e)); })
              .reverse()
              .value();
    };
    
    var fetchArticles = function() {
      $scope.searchLoading = true;
      
      $http.post('/api/articles/similar', {articleId: $routeParams.articleId, startdatemillis: $scope.timeRange, limit: 25})
        .success(function(data) {
          $scope.searchLoading = false;
          $scope.shownArticlesCount = data.articles.length;
          $scope.totalresults = data.totalresults;
          $scope.ranges = splitByRanges(data.articles);
          if (data.articles.length > 0) $scope.dateFormat = createRange(data.articles).format;
        })
        .error(function(msg, status) {
          $scope.searchLoading = false;
          FlashMessage.add({type: 'error', content: i18n.getByKey('flash.search.error')});
          log.error('Error while fetching articles', msg, status);
        });
    };
    
    
    //
    // Scope functions
    //
    
    $scope.changeTimeStart = function() {
      fetchArticles();
    };
    
    $scope.formatDate = function(date) {
      // Using plot since it's localization mechanism is way more flexible than angular's
      return $.plot.formatDate(typeof(date) !== 'object' ? new Date(parseInt(date)) : date, $scope.dateFormat, monthNames, dayNames);
    };
    
    $scope.backToWatch = function() {
      $location.path('/search');
    };
    
    $scope.showArticle = function() {
      window.open($scope.selectedArticle.link, '_blank').focus();
    };
    
    //
    // Initial calls
    //
    
    $http.get('/api/articles/' + encodeURIComponent($routeParams.articleId))
      .success(function(data) {
        $scope.selectedArticleLoading = false;
        $scope.selectedArticle = _.compose(shortenHeadline(100), shortenDescription(400))(data);
      })
      .error(function(msg, status) {
        $scope.selectedArticleLoading = false;
        FlashMessage.add({type: 'error', content: i18n.getByKey('flash.search.error')});
        log.error('Error while fetching selected article data', msg, status);
      });
    
    fetchArticles();
  }]);
}(angular));