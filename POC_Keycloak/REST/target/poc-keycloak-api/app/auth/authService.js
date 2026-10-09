(function () {
    'use strict';

    angular.module('pocKeycloakApp.auth')
        .service('authService', AuthService);

    AuthService.$inject = ['$http', '$q', '$window'];

    function AuthService($http, $q, $window) {
        var accessToken = null;
        var pendingRegistration = null;
        var service = this;

        this.login = function (credentials) {
            service.clearSession();
            service.clearPendingRegistration();
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
                service.clearPendingRegistration();
            });
        };

        this.savePendingRegistration = function (response, username) {
            var data = response && response.data ? response.data : {};
            pendingRegistration = {
                username: username,
                registrationTicket: data.registration_ticket || data.registrationTicket,
                missingFields: data.missing_fields || data.missingFields || {},
                message: data.message || 'Complete seu cadastro para continuar.'
            };
        };

        this.getPendingRegistration = function () { return pendingRegistration; };
        this.clearPendingRegistration = function () { pendingRegistration = null; };
        this.hasPendingRegistration = function () {
            return !!(pendingRegistration && pendingRegistration.registrationTicket);
        };

        this.completeRegistration = function (input) {
            return $http.post('api/v1/users/complete-registration', input);
        };

        this.getAccessToken = function () {
            return accessToken;
        };

        // Roles are read only to improve the experience; the API remains the
        // authority and enforces the same permissions server-side.
        this.getRoles = function () {
            if (!accessToken) {
                return [];
            }
            try {
                var parts = accessToken.split('.');
                if (parts.length < 2) {
                    return [];
                }
                var payload = JSON.parse(decodeBase64(parts[1]));
                var roles = (payload.realm_access && payload.realm_access.roles) || [];
                roles = roles.concat((payload.groups || []).map(function (group) {
                    return group.replace(/^\//, '');
                }));
                angular.forEach(payload.resource_access || {}, function (client) {
                    roles = roles.concat(client.roles || []);
                });
                return roles;
            } catch (ignore) {
                return [];
            }
        };

        this.hasAnyRole = function (allowed) {
            var roles = service.getRoles();
            return allowed.some(function (role) { return roles.indexOf(role) !== -1; });
        };

        this.getUsername = function () {
            if (!accessToken) return null;
            try {
                var parts = accessToken.split('.');
                if (parts.length < 2) return null;
                var payload = JSON.parse(decodeBase64(parts[1]));
                return payload.preferred_username || payload.username || payload.email || null;
            } catch (ignore) { return null; }
        };

        this.setAccessToken = function (token) {
            accessToken = token;
        };

        this.clearSession = function () {
            accessToken = null;
        };

        function decodeBase64(value) {
            // JWT uses base64url and may omit padding.
            var normalized = value.replace(/-/g, '+').replace(/_/g, '/');
            while (normalized.length % 4) { normalized += '='; }
            return $window.atob(normalized);
        }
    }
}());
