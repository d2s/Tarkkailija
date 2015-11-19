'use strict';

var tarkkailija = tarkkailija || {};
tarkkailija.Utils = {};

//underscore mixins
_.mixin({
  deepClone: function(obj) {
    // Snatched from: http://stackoverflow.com/questions/728360/most-elegant-way-to-clone-a-javascript-object#answer-728694
    // Handle the 3 simple types, and null or undefined
    if (null == obj || "object" != typeof obj) return obj;

    // Handle Date
    if (obj instanceof Date) {
        var copy = new Date();
        copy.setTime(obj.getTime());
        return copy;
    }

    // Handle Array
    if (obj instanceof Array) {
        var copy = [];
        for (var i = 0, len = obj.length; i < len; i++) {
            copy[i] = _.deepClone(obj[i]);
        }
        return copy;
    }

    // Handle Object
    if (obj instanceof Object) {
        var copy = {};
        for (var attr in obj) {
            if (obj.hasOwnProperty(attr)) copy[attr] = _.deepClone(obj[attr]);
        }
        return copy;
    }

    throw new Error("Unable to copy obj! Its type isn't supported.");
  },
  
  strFormat: function(/*arguments*/) {
    var args = Array.prototype.slice.call(arguments);
    
    var params = args.slice(1);
    
    var formatted = args[0];
    for(var arg in params) {
      formatted = formatted.replace('{' + arg + '}', params[arg]);
    }
    return formatted;
  },
  
  strEndsWith: function(str, suffix) {
    return str.indexOf(suffix, str.length - suffix.length) !== -1;
  }
});

tarkkailija.Utils.dateToStr = function(date) {
  function prefixZeroes(number, length) {
    var str = '';
    for (var i = number.toString().length; i < length; i++) {
      str += '0';
    }
    return str + number;
  }

  return _.strFormat('{0}:{1}:{2}.{3}',
      prefixZeroes(date.getHours(), 2),
      prefixZeroes(date.getMinutes(), 2),
      prefixZeroes(date.getSeconds(), 2),
      prefixZeroes(date.getMilliseconds(), 3));
};

tarkkailija.Utils.getLogger = function(obj) {
  function createLogger(loggingFunction) {
    return function() {
      if (window.console && (typeof console[loggingFunction] === 'function' || typeof console[loggingFunction] === 'object')) {
        var log = null;
        var args = Array.prototype.slice.call(arguments);
        if (typeof console[loggingFunction] === 'function') {
          // typical case
          log = console[loggingFunction];
        } else {
          // IE9 case
          log = Function.prototype.bind.call(console[loggingFunction], console);
          for (var key in args) {
            if (args.hasOwnProperty(key)) {args[key] = args[key] + ' ';}
          }
        }
        args.splice(0, 0, tarkkailija.Utils.dateToStr(new Date()) + ' [' + obj + '] -');
        log.apply(console, args);
      }
      return arguments.length === 1 ? arguments[0] : arguments;
    };
  }

  var logger = createLogger('log');
  logger.error = createLogger('error');
  logger.warn = createLogger('warn');
  logger.info = createLogger('info');
  logger.debug = createLogger('debug');

  return logger;
};

tarkkailija.dates = tarkkailija.dates || {};

tarkkailija.dates.fromNow = function(func, val) {
  return func(new Date(), val);
};

tarkkailija.dates.plusHours = function(d, hours) {
  return d.setHours(d.getHours() + hours);
};

tarkkailija.dates.minusYears = function(d, years) {
  return d.setFullYear(d.getFullYear() - years);
};

tarkkailija.dates.minusMonths = function(d, months) {
  return d.setMonth(d.getMonth() - months);
};

tarkkailija.dates.minusDays = function(d, days) {
  return d.setDate(d.getDate() - days);
};

tarkkailija.dates.daysBetween = function(d1, d2) {
  var start = typeof d1 === 'object' ? d1.getTime() : d1;
  var end = typeof d2 === 'object' ? d2.getTime() : d2;
  
  return Math.ceil(Math.abs((end - start) / 1000 / 60 / 60 / 24));
};

tarkkailija.dates.floorYear = function(d) {
  var newDate = new Date(0);
  newDate.setUTCFullYear(d.getUTCFullYear());
  return newDate;
};

tarkkailija.dates.floorMonth = function(d) {
  var newDate = new Date(0);
  newDate.setUTCFullYear(d.getUTCFullYear());
  newDate.setUTCMonth(d.getUTCMonth());
  return newDate;
};

tarkkailija.dates.floorDay = function(d) {
  var newDate = new Date(0);
  newDate.setUTCFullYear(d.getUTCFullYear());
  newDate.setUTCMonth(d.getUTCMonth());
  newDate.setUTCDate(d.getUTCDate());
  return newDate;
};

tarkkailija.dates.ceilYear = function(d) {
  var newDate = tarkkailija.dates.floorYear(d);
  newDate.setUTCFullYear(newDate.getUTCFullYear() + 1);
  return newDate;
};

tarkkailija.dates.ceilMonth = function(d) {
  var newDate = tarkkailija.dates.floorMonth(d);
  newDate.setUTCMonth(newDate.getUTCMonth() + 1);
  return newDate;
};

tarkkailija.dates.ceilDay = function(d) {
  var newDate = tarkkailija.dates.floorDay(d);
  newDate.setUTCDate(newDate.getUTCDate() + 1);
  return newDate;
};
