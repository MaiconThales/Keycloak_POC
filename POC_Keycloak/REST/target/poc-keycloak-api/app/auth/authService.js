(function () {
    'use strict';

    angular.module('pocKeycloakApp.auth')
        .service('authService', AuthService);

    AuthService.$inject = ['$http', '$q', '$window'];

    function AuthService($http, $q, $window) {
        var accessToken = null;
        var service = this;

        this.login = function (credentials) {
            service.clearSession();
            return $http.post('api/v1/auth/login', {
                username: credentials.username,
                password: credentials.password
            }).then(function (response) {
                var token = response.data && response.data.access_token;
                if (typeof token !== 'string' || !token.trim()) {
                    return $q.reject({
                        data: { message: 'Resposta de autenticação inválida.' }
                    });
                }
                service.setAccessToken(token);
            });
        };

        this.getAccessToken = function () {
            return accessToken;
        };

        /*
         * Route guards must use the authentication state, not the requested
         * URL.  The exp check is deliberately only a client-side convenience;
         * the API remains responsible for validating the JWT signature.
         */
        this.isAuthenticated = function () {
            if (typeof accessToken !== 'string' || !accessToken.trim()) {
                return false;
            }
            var payload = readJwtPayload(accessToken);
            return !!payload && (typeof payload.exp !== 'number'
                || payload.exp * 1000 > Date.now());
        };

        /* Exposes only the decoded claims needed by presentation components.
         * The API remains the authority for signature and authorization checks. */
        this.getTokenClaims = function () {
            return service.isAuthenticated() ? readJwtPayload(accessToken) : null;
        };

        /*
         * Claims are permissions as well as groups.  Compare the claim's
         * actual name; do not infer a base group from a granular permission
         * such as Admin-Read or Admin-Write.
         */
        this.hasExactRoleOrGroup = function (group) {
            var claims = service.getTokenClaims();
            return !!claims && collectRolesAndGroups(claims).some(function (value) {
                return normalizeClaim(value) === normalizeClaim(group);
            });
        };

        /* Backwards-compatible name with the corrected exact-match behavior. */
        this.hasRoleOrGroup = this.hasExactRoleOrGroup;

        this.hasAnyExactRoleOrGroup = function (groups) {
            return angular.isArray(groups) && groups.some(function (group) {
                return service.hasExactRoleOrGroup(group);
            });
        };

        /* One policy is shared by the sidebar and route guards. */
        this.canReadProducts = function () {
            return service.hasAnyExactRoleOrGroup([
                'Admin', 'Sub-Admin', 'User',
                'Admin-Read', 'Sub-Admin-Read', 'User-Read'
            ]);
        };

        this.canReadUsers = function () {
            return service.hasAnyExactRoleOrGroup(['Admin', 'Admin-Read']);
        };

        this.setAccessToken = function (token) {
            accessToken = token;
        };

        this.clearSession = function () {
            accessToken = null;
        };

        /*
         * Logout is deliberately owned by the authentication service so every
         * entry point invalidates the same in-memory session before navigating.
         * replace() also prevents the protected view from being restored with
         * the browser Back button.
         */
        this.logout = function () {
            service.clearSession();
            $window.location.replace('#!/login');
        };

        function readJwtPayload(token) {
            try {
                var encoded = token.split('.')[1];
                if (!encoded) {
                    return null;
                }
                encoded = encoded.replace(/-/g, '+').replace(/_/g, '/');
                while (encoded.length % 4) {
                    encoded += '=';
                }
                return JSON.parse($window.atob(encoded));
            } catch (ignore) {
                return null;
            }
        }

        function collectRolesAndGroups(claims) {
            var values = [];
            addValues(values, claims.roles);
            addValues(values, claims.groups);
            addValues(values, claims.realm_access && claims.realm_access.roles);
            angular.forEach(claims.resource_access, function (client) {
                addValues(values, client && client.roles);
            });
            return values;
        }

        function addValues(target, values) {
            if (angular.isArray(values)) {
                Array.prototype.push.apply(target, values);
            }
        }

        function normalizeClaim(value) {
            return String(value || '').split('/').pop().toLowerCase();
        }
    }
}());
