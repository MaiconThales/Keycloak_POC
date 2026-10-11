(function () {
    'use strict';

    angular.module('pocKeycloakApp.reviews')
        .controller('ReviewsController', ReviewsController);

    ReviewsController.$inject = ['reviewsService', 'productsService', 'authService', '$window'];

    function ReviewsController(reviewsService, productsService, authService, $window) {
        var vm = this;
        vm.items = [];
        vm.products = [];
        vm.input = {};
        vm.loading = false;
        vm.saving = false;
        vm.listError = null;
        vm.formError = null;
        vm.success = null;

        vm.isOwner = function (review) {
            var claims = authService.getTokenClaims();
            return !!claims && !!claims.sub && review
                && String(review.user_keycloak_id || review.userKeycloakId) === String(claims.sub);
        };

        vm.productName = function (productId) {
            var product = vm.products.filter(function (item) {
                return String(item.id) === String(productId);
            })[0];
            return product ? product.name : ('Produto #' + productId);
        };

        vm.load = function () {
            if (vm.loading) { return; }
            vm.loading = true;
            vm.listError = null;
            return reviewsService.findAll(vm.products).then(function (items) {
                vm.items = items;
            }, function () {
                vm.listError = 'Não foi possível carregar as reviews. Tente novamente.';
            }).finally(function () {
                vm.loading = false;
            });
        };

        vm.create = function (form) {
            if (vm.saving || form.$invalid || !vm.input.productId || !vm.input.comment
                    || !vm.input.comment.trim()) {
                vm.formError = 'Informe o produto e um comentário válido.';
                return;
            }
            vm.saving = true;
            vm.formError = null;
            vm.success = null;
            return reviewsService.create(vm.input).then(function () {
                vm.input = {};
                form.$setPristine();
                form.$setUntouched();
                vm.success = 'Review cadastrada com sucesso.';
                return vm.load();
            }, function (rejection) {
                vm.formError = rejection.status === 400
                    ? 'Dados inválidos. Revise o formulário.'
                    : 'Não foi possível cadastrar a review.';
            }).finally(function () { vm.saving = false; });
        };

        vm.beginEdit = function (review) {
            if (vm.isOwner(review)) {
                review.editing = true;
                review.editComment = review.comment;
            }
        };

        vm.cancelEdit = function (review) { review.editing = false; };

        vm.update = function (review) {
            if (!vm.isOwner(review) || !review.editComment || !review.editComment.trim()) {
                return;
            }
            review.busy = true;
            vm.formError = null;
            return reviewsService.update({ id: review.id, comment: review.editComment })
                .then(function (updated) {
                    review.comment = updated.comment;
                    review.editing = false;
                    vm.success = 'Review atualizada com sucesso.';
                }, function () {
                    vm.formError = 'Não foi possível atualizar a review.';
                }).finally(function () { review.busy = false; });
        };

        vm.remove = function (review) {
            if (!vm.isOwner(review) || !$window.confirm('Excluir esta review?')) { return; }
            review.busy = true;
            return reviewsService.remove(review.id).then(function () {
                vm.items = vm.items.filter(function (item) { return item !== review; });
                vm.success = 'Review excluída com sucesso.';
            }, function () {
                vm.formError = 'Não foi possível excluir a review.';
            }).finally(function () { review.busy = false; });
        };

        productsService.findAll().then(function (items) {
            vm.products = items;
            vm.load();
        }, function () {
            vm.listError = 'Não foi possível carregar os produtos das reviews.';
        });
    }
}());
