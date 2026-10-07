(function () {
    'use strict';

    angular.module('pocKeycloakApp')
        .run(initializeNavigation);

    initializeNavigation.$inject = ['$rootScope', '$location'];

    function initializeNavigation($rootScope, $location) {
        $rootScope.$watch(function () {
            return $location.path();
        }, function (path) {
            $rootScope.currentView = path === '/login' ? 'login' : 'products';
        });
    }
}());
