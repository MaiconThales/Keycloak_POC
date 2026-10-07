(function () {
    'use strict';

    angular.module('pocKeycloakApp.auth')
        .service('authService', AuthService);

    AuthService.$inject = ['$http', '$q'];

    function AuthService($http, $q) {
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

        this.setAccessToken = function (token) {
            accessToken = token;
        };

        this.clearSession = function () {
            accessToken = null;
        };
    }
}());
