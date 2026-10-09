(function () {
    'use strict';

    angular.module('pocKeycloakApp.reviews').service('reviewsService', ReviewsService);
    ReviewsService.$inject = ['$http'];

    function ReviewsService($http) {
        this.find = function (filters) {
            var params = {};
            if (filters.productId) params.productId = filters.productId;
            if (filters.rating) params.rating = filters.rating;
            return $http.get('api/v1/reviews', { params: params }).then(function (response) {
                return response.data || [];
            });
        };
        this.create = function (input) {
            return $http.post('api/v1/reviews', input).then(function (response) { return response.data; });
        };
        this.update = function (id, input) {
            return $http.put('api/v1/reviews/' + encodeURIComponent(id), input)
                .then(function (response) { return response.data; });
        };
        this.remove = function (id) {
            return $http.delete('api/v1/reviews/' + encodeURIComponent(id));
        };
    }
}());
