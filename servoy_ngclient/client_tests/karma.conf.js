module.exports = function(config){
  config.set({
    basePath : '.',
    
    preprocessors: {
        '../war/servoydefault/**/*.html': ['ng-html2js']
    },
    files : [
       'lib/jquery.js',
       'lib/angular.js',
       'lib/angular-mocks.js',  
       '../../sablo/META-INF/resources/sablo/js/*.js',
       'lib/phantomjs.polyfill.js',
       '../war/js/**/*.js',
       '../war/servoydefault/*/*.js',
       './test/**/*.js',
       '../war/servoydefault/*/*.html'
    ],
    exclude : [
	  '../war/servoydefault/tabpanel/tabpanel_server.js',
	  '../war/js/**/*.min.js',
	  '../war/js/**/angular1.3.0.js'
      /*'app/lib/angular/angular-loader.js',
      'app/lib/angular/*.min.js',
      'app/lib/angular/angular-scenario.js'*/
    ],
    ngHtml2JsPreprocessor: {
        // setting this option will create only a single module that contains templates
        // from all the files, so you can load them all with module('foo')
        moduleName: 'servoy-components',
        
        cacheIdFromPath: function(filepath) {
            return filepath.replace(/.*?\/servoydefault\/(.*)/,"servoydefault/$1");
        },
    },

    frameworks: ['jasmine'],
    browsers : ['PhantomJS'],//

    /*plugins : [    <- not needed since karma loads by default all sibling plugins that start with karma-*
            'karma-junit-reporter',
            'karma-chrome-launcher',
            'karma-firefox-launcher',
            'karma-script-launcher',
            'karma-jasmine'
            ],*/
	browserNoActivityTimeout:999999,
    singleRun: true,
    //autoWatch : true,
    reporters: ['dots', 'junit'],
    junitReporter: {
          outputFile: 'test-results.xml'
    }
  /*,  alternative format
    junitReporter : {
      outputFile: 'test_out/unit.xml',
      suite: 'unit'
    }*/
  });
};
