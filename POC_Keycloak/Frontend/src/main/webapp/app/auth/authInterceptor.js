(function () {
    'use strict';

    angular.module('pocKeycloakApp.auth')
        .factory('authInterceptor', AuthInterceptor);

    AuthInterceptor.$inject = ['$q', '$window', '$location', '$injector'];

    function AuthInterceptor($q, $window, $location, $injector) {
        return {
            request: function (config) {
                var token = $injector.get('authService').getAccessToken();
                if (token && isApiRequest(config.url)) {
                    config.headers = config.headers || {};
                    config.headers.Authorization = 'Bearer ' + token;
                }
                return config;
            },
            responseError: function (rejection) {
                if (isApiRequest(rejection.config && rejection.config.url)
                        && (rejection.status === 401 || rejection.status === 403)) {
                    $injector.get('authService').clearSession();
                    $location.path('/login');
                }
                return $q.reject(rejection);
            }
        };

        function isApiRequest(url) {
            if (typeof url !== 'string') {
                return false;
            }
            var target = $window.document.createElement('a');
            target.href = url;
            var base = $window.document.createElement('a');
            base.href = '.';
            var apiPath = base.pathname.replace(/\/?$/, '/') + 'api/v1/';
            return target.protocol === base.protocol && target.host === base.host
                && target.pathname.indexOf(apiPath) === 0;
        }
    }
}());
