module.exports = function(config){
  config.set({
    basePath : '.',

    preprocessors: {
        '../war/servoydefault/**/*.html': ['ng-html2js']
    },
    files : [
         {pattern: 'fileResources/**/*', watched: true, included: false, served: true},
         
         // libraries for testing and angular
         'lib/jquery.js',
         'lib/phantomjs.polyfill.js',
         '../war/js/angular_1.*.js',
         'lib/angular-mocks*.js',
         
         // sablo and ngclient scripts
         '../../../sablo/sablo/META-INF/resources/sablo/js/*.js', /* use this when running from Git */
         '../../sablo/META-INF/resources/sablo/js/*.js',  /* use this when running from SVN-git bridge */
         '../war/js/numeral.js',
         '../war/js/**/*.js',
         
         // components
         '../war/servoydefault/*/*.js',
         '../war/servoydefault/*/*/*.js',
         '../war/servoyservices/component_custom_property/*.js',
         '../war/servoyservices/custom_json_array_property/*.js',
         '../war/servoyservices/custom_json_object_property/*.js',
         '../war/servoyservices/foundset_linked_property/*.js',
         '../war/servoyservices/foundset_custom_property/*.js',
         '../war/servoyservices/foundset_viewport_module/*.js',

         // templates
         '../war/servoydefault/**/*.html',

         // tests
         'test/**/*.js'
    ],
    exclude : [
         	  '../war/servoydefault/**/*_server.js',
         	  '../war/js/**/*.min.js'
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
    browsers : ['Chrome','Firefox'],//

    /*plugins : [    <- not needed since karma loads by default all sibling plugins that start with karma-*
            'karma-junit-reporter',
            'karma-chrome-launcher',
            'karma-firefox-launcher',
            'karma-script-launcher',
            'karma-jasmine'
            ],*/
	browserNoActivityTimeout:999999,
    singleRun: false,
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
