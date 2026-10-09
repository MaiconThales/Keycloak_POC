(function () {
    'use strict';

    angular.module('pocKeycloakApp.products')
        .service('productsService', ProductsService);

    ProductsService.$inject = ['$http'];

    function ProductsService($http) {
        this.findAll = function () {
            return $http.get('api/v1/products').then(function (response) {
                return response.data;
            });
        };

        this.create = function (product) {
            return $http.post('api/v1/products', {
                name: product.name,
                price: product.price,
                sku: product.sku
            }).then(function (response) {
                return response.data;
            });
        };
    }
}());
