(function () {
    'use strict';

    /*
     * The application does not use ngRoute: the server serves one SPA entry
     * point and the hash is used for navigation.  Keep the access policy in a
     * dedicated run block nevertheless, so a URL can never select a protected
     * view by itself.
     */
    angular.module('pocKeycloakApp')
        .run(protectRoutes);

    protectRoutes.$inject = ['$rootScope', '$location', 'authService'];

    function protectRoutes($rootScope, $location, authService) {
        $rootScope.$on('$locationChangeStart', function (event) {
            var path = $location.path();

            if (path === '/login') {
                return;
            }

            if (path === '/products' && authService.isAuthenticated()
                    && authService.canReadProducts()) {
                return;
            }

            if (path === '/users' && authService.isAuthenticated()
                    && authService.canReadUsers()) {
                return;
            }

            /*
             * There are only two client-side routes.  In particular, do not
             * send an authenticated user from an unknown URL to /products:
             * that would make arbitrary URLs behave as an implicit protected
             * route.  Unknown URLs must have the same deterministic fallback
             * regardless of the current authentication state.
             */
            event.preventDefault();
            if (!authService.isAuthenticated()) {
                authService.clearSession();
            }
            $location.path('/login');
        });
    }
}());
