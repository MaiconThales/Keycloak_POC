(function () {
    'use strict';

    angular.module('pocKeycloakApp')
        .config(configureRoutes)
        .run(initializeNavigation);

    configureRoutes.$inject = ['$routeProvider'];

    // Keep this allow-list deliberately small. A route is private by default;
    // adding a route must not accidentally make it public by omitting metadata.
    var PUBLIC_ROUTES = {
        '/login': true
    };

    function configureRoutes($routeProvider) {
        $routeProvider
            .when('/login', {
                templateUrl: 'app/auth/login.html',
                viewName: 'login'
            })
            .when('/products', {
                templateUrl: 'app/products/products.html',
                authRequired: true,
                viewName: 'products'
            })
            .when('/reviews', {
                templateUrl: 'app/reviews/reviews.html',
                authRequired: true,
                viewName: 'reviews'
            })
            .when('/complete-registration', {
                templateUrl: 'app/auth/completeRegistration.html',
                authRequired: true,
                viewName: 'complete-registration'
            })
            .otherwise({ redirectTo: '/login' });
    }

    initializeNavigation.$inject = ['$rootScope', '$location', 'authService'];

    function initializeNavigation($rootScope, $location, authService) {
        $rootScope.$on('$routeChangeStart', function (event, next) {
            var routePath = next && (next.originalPath
                || (next.$$route && next.$$route.originalPath));
            var requestedPath = routePath || $location.path();

            // /login is the only public SPA route. Keep the guard independent
            // of authRequired so future routes remain protected by default.
            if (requestedPath && !PUBLIC_ROUTES[requestedPath]
                    && !authService.getAccessToken()
                    && !(requestedPath === '/complete-registration'
                        && authService.hasPendingRegistration())) {
                event.preventDefault();
                $location.path('/login');
            }
        });

        $rootScope.$on('$routeChangeSuccess', function (event, current) {
            $rootScope.currentView = current && current.$$route
                ? current.$$route.viewName || current.$$route.originalPath
                : null;
        });
    }
}());
