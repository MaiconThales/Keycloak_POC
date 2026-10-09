(function () {
    'use strict';

    angular.module('pocKeycloakApp', [
        'ngRoute',
        'pocKeycloakApp.auth',
        'pocKeycloakApp.products',
        'pocKeycloakApp.reviews'
    ]);
}());
