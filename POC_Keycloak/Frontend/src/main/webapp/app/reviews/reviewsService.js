(function () {
    'use strict';

    angular.module('pocKeycloakApp.reviews')
        .service('reviewsService', ReviewsService);

    ReviewsService.$inject = ['$http', '$q'];

    function ReviewsService($http, $q) {
        var baseUrl = 'api/v1/reviews';

        /* The existing API requires productId on GET; aggregate its supported
         * product-scoped responses into the screen's complete list. */
        this.findAll = function (products) {
            var requests = (products || []).map(function (product) {
                return $http.get(baseUrl, { params: { productId: product.id } })
                    .then(function (response) { return response.data || []; });
            });
            return $q.all(requests).then(function (groups) {
                return groups.reduce(function (items, group) {
                    return items.concat(group);
                }, []);
            });
        };

        this.create = function (review) {
            return $http.post(baseUrl, {
                product_id: review.productId,
                comment: review.comment
            }).then(function (response) {
                return response.data;
            });
        };

        this.update = function (review) {
            return $http.put(baseUrl + '/' + encodeURIComponent(review.id), {
                comment: review.comment
            }).then(function (response) {
                return response.data;
            });
        };

        this.remove = function (id) {
            return $http.delete(baseUrl + '/' + encodeURIComponent(id));
        };
    }
}());
