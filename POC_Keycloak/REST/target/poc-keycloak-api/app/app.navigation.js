(function () {
    'use strict';

    angular.module('pocKeycloakApp')
        .run(initializeNavigation);

    initializeNavigation.$inject = ['$rootScope', '$location', 'authService'];

    function initializeNavigation($rootScope, $location, authService) {
        $rootScope.$watch(function () {
            return $location.path();
        }, function (path) {
            if (path === '/products' && authService.isAuthenticated()
                    && authService.canReadProducts()) {
                $rootScope.currentView = 'products';
            } else if (path === '/users' && authService.isAuthenticated()
                    && authService.canReadUsers()) {
                $rootScope.currentView = 'users';
            } else {
                $rootScope.currentView = 'login';
            }
        });
    }
}());
