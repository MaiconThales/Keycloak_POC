(function () {
    'use strict';

    angular.module('pocKeycloakApp.auth')
        .config(configureAuth);

    configureAuth.$inject = ['$httpProvider'];

    function configureAuth($httpProvider) {
        $httpProvider.interceptors.push('authInterceptor');
    }
}());
