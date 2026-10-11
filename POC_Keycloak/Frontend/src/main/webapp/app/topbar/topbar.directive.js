(function () {
    'use strict';

    angular.module('pocKeycloakApp.topbar')
        .directive('pocTopbar', pocTopbar);

    pocTopbar.$inject = ['authService', '$rootScope'];

    function pocTopbar(authService, $rootScope) {
        return {
            restrict: 'E',
            replace: true,
            templateUrl: 'app/topbar/topbar.html',
            scope: true,
            link: function (scope) {
                scope.sidebarOpen = false;
                scope.isAuthenticated = authService.isAuthenticated;
                scope.logout = authService.logout;
                scope.toggleSidebar = function () {
                    scope.sidebarOpen = !scope.sidebarOpen;
                    $rootScope.$broadcast('sidebar:toggle', scope.sidebarOpen);
                };
            }
        };
    }
}());
