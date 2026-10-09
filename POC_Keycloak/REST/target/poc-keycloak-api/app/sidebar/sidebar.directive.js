(function () {
    'use strict';

    angular.module('pocKeycloakApp.sidebar')
        .directive('pocSidebar', pocSidebar);

    pocSidebar.$inject = ['authService'];

    function pocSidebar(authService) {
        return {
            restrict: 'E',
            replace: true,
            templateUrl: 'app/sidebar/sidebar.html',
            // Keep the directive on a child scope so currentView is inherited
            // from $rootScope and remains observable when navigation changes.
            // An isolated scope would hide the global value from the template.
            scope: true,
            link: function (scope) {
                scope.sidebarOpen = false;
                scope.isAuthenticated = authService.isAuthenticated;
                scope.$on('sidebar:toggle', function (event, isOpen) {
                    scope.sidebarOpen = isOpen;
                });
                scope.canSeeUsers = function () {
                    return authService.canReadUsers();
                };
                scope.canSeeProducts = function () {
                    return authService.canReadProducts();
                };
            }
        };
    }
}());
