(function () {
    'use strict';

    angular.module('pocKeycloakApp.reviews').controller('ReviewsController', ReviewsController);
    ReviewsController.$inject = ['reviewsService', 'productsService', 'authService'];

    function ReviewsController(reviewsService, productsService, authService) {
        var vm = this;
        vm.items = [];
        vm.products = [];
        vm.filters = {};
        vm.input = {};
        vm.loading = false;
        vm.productsLoading = false;
        vm.saving = false;
        vm.editSaving = false;
        vm.deleteSaving = false;
        vm.editing = null;
        vm.deleting = null;
        vm.error = null;
        vm.formError = null;
        vm.editError = null;
        vm.deleteError = null;
        vm.success = null;

        vm.canCreate = function () {
            return authService.hasAnyRole(['User', 'User-Write']);
        };
        vm.canModerate = function (action) {
            return authService.hasAnyRole(action === 'delete'
                ? ['Admin', 'Sub-Admin', 'Admin-Delete', 'Sub-Admin-Delete']
                : ['Admin', 'Sub-Admin', 'Admin-Update', 'Sub-Admin-Update']);
        };
        vm.canEdit = function (review) {
            return vm.canModerate('update') || (review && review.authorUsername === authService.getUsername());
        };
        vm.canDelete = function (review) {
            return vm.canModerate('delete') || (review && review.authorUsername === authService.getUsername());
        };
        vm.stars = function (rating) {
            var result = [];
            for (var i = 1; i <= 5; i += 1) result.push(i <= Number(rating));
            return result;
        };
        vm.load = function () {
            if (vm.loading) return;
            vm.loading = true; vm.error = null;
            return reviewsService.find(vm.filters).then(function (items) {
                vm.items = items;
            }, function () {
                vm.error = 'Não foi possível carregar as avaliações. Tente novamente.';
            }).finally(function () { vm.loading = false; });
        };
        vm.loadProducts = function () {
            vm.productsLoading = true;
            return productsService.findAll().then(function (items) { vm.products = items; }, function () {
                vm.error = 'Não foi possível carregar os produtos para o formulário.';
            }).finally(function () { vm.productsLoading = false; });
        };
        vm.create = function (form) {
            vm.formError = null; vm.success = null;
            if (!vm.canCreate()) { vm.formError = 'Você não tem permissão para criar avaliações.'; return; }
            if (form.$invalid || !vm.input.productId || !vm.input.rating || !vm.input.comment || !vm.input.comment.trim()) {
                vm.formError = 'Informe produto, nota de 1 a 5 e comentário.'; return;
            }
            vm.saving = true;
            return reviewsService.create({ productId: vm.input.productId, rating: vm.input.rating, comment: vm.input.comment.trim() })
                .then(function () { vm.input = {}; form.$setPristine(); form.$setUntouched(); vm.success = 'Avaliação criada com sucesso.'; return vm.load(); },
                    function (response) { vm.formError = reviewError(response, 'criar'); })
                .finally(function () { vm.saving = false; });
        };
        vm.openEdit = function (review) {
            if (!vm.canEdit(review)) return;
            vm.editing = review; vm.editInput = { productId: review.productId, rating: review.rating, comment: review.comment }; vm.editError = null;
        };
        vm.closeEdit = function () { if (!vm.editSaving) vm.editing = null; };
        vm.update = function (form) {
            if (vm.editSaving || !vm.editing) return;
            if (form.$invalid || !vm.editInput.rating || !vm.editInput.comment || !vm.editInput.comment.trim()) { vm.editError = 'Informe nota e comentário válidos.'; return; }
            vm.editSaving = true;
            return reviewsService.update(vm.editing.id, { productId: vm.editInput.productId, rating: vm.editInput.rating, comment: vm.editInput.comment.trim() })
                .then(function () { vm.editing = null; vm.success = 'Avaliação atualizada com sucesso.'; return vm.load(); }, function (response) { vm.editError = reviewError(response, 'atualizar'); })
                .finally(function () { vm.editSaving = false; });
        };
        vm.openDelete = function (review) { if (vm.canDelete(review)) { vm.deleting = review; vm.deleteError = null; } };
        vm.closeDelete = function () { if (!vm.deleteSaving) vm.deleting = null; };
        vm.remove = function () {
            if (vm.deleteSaving || !vm.deleting) return;
            vm.deleteSaving = true;
            return reviewsService.remove(vm.deleting.id).then(function () { vm.deleting = null; vm.success = 'Avaliação removida com sucesso.'; return vm.load(); }, function (response) { vm.deleteError = reviewError(response, 'remover'); })
                .finally(function () { vm.deleteSaving = false; });
        };
        function reviewError(response, action) {
            if (response.status === 401) return 'Sua sessão expirou. Entre novamente.';
            if (response.status === 403) return 'Você não tem permissão para ' + action + ' esta avaliação.';
            if (response.status === 400) return 'Dados inválidos ou avaliação duplicada.';
            if (response.status === 404) return 'Avaliação não encontrada. Atualize a lista.';
            return 'Não foi possível ' + action + ' a avaliação. Tente novamente.';
        }
        vm.loadProducts();
        vm.load();
    }
}());
