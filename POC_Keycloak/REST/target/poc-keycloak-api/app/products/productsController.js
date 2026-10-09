(function () {
    'use strict';

    angular.module('pocKeycloakApp.products')
        .controller('ProductsController', ProductsController);

    ProductsController.$inject = ['productsService', 'authService'];

    function ProductsController(productsService, authService) {
        var vm = this;
        vm.items = [];
        vm.input = {};
        vm.loading = false;
        vm.saving = false;
        vm.editing = null;
        vm.deleting = null;
        vm.editSaving = false;
        vm.deleteSaving = false;
        vm.listError = null;
        vm.createError = null;
        vm.success = null;

        vm.isAuthenticated = function () {
            return !!authService.getAccessToken();
        };

        vm.canCreate = function () {
            return vm.isAuthenticated() && authService.hasAnyRole([
                'Admin', 'Sub-Admin', 'Admin-Write', 'Sub-Admin-Write'
            ]);
        };
        vm.canUpdate = function () {
            return authService.hasAnyRole([
                'Admin', 'Sub-Admin', 'Admin-Write', 'Sub-Admin-Write',
                'Admin-Update', 'Sub-Admin-Update'
            ]);
        };
        vm.canDelete = function () {
            return authService.hasAnyRole(['Admin', 'Admin-Write', 'Admin-Delete']);
        };

        vm.load = function () {
            if (vm.loading) {
                return;
            }
            vm.loading = true;
            vm.listError = null;
            return productsService.findAll().then(function (items) {
                vm.items = items;
            }, function () {
                vm.listError = 'Não foi possível carregar os produtos. Tente novamente.';
            }).finally(function () {
                vm.loading = false;
            });
        };

        vm.create = function (form) {
            if (vm.saving) {
                return;
            }
            vm.createError = null;
            vm.success = null;
            if (!vm.isAuthenticated()) {
                vm.createError = 'Entre no sistema para criar um produto.';
                return;
            }
            if (!vm.canCreate()) {
                vm.createError = 'Você não tem permissão para criar produtos.';
                return;
            }
            if (form.$invalid || !vm.input.name || !vm.input.name.trim()
                    || !vm.input.sku || !vm.input.sku.trim()) {
                vm.createError = 'Informe nome, preço e SKU válidos.';
                return;
            }
            vm.saving = true;
            return productsService.create(vm.input).then(function () {
                vm.input = {};
                form.$setPristine();
                form.$setUntouched();
                vm.success = 'Produto criado com sucesso.';
                return vm.load();
            }, function (rejection) {
                if (rejection.status === 403) {
                    vm.createError = 'Você não tem permissão para criar produtos.';
                } else if (rejection.status === 401) {
                    vm.createError = 'Sua sessão expirou. Entre novamente.';
                } else if (rejection.status === 400) {
                    vm.createError = 'Dados inválidos. Revise os campos do produto.';
                } else {
                    vm.createError = 'Não foi possível criar o produto. Tente novamente.';
                }
            }).finally(function () {
                vm.saving = false;
            });
        };

        vm.openEdit = function (product) {
            if (!vm.canUpdate()) { return; }
            vm.editing = product;
            vm.editInput = { name: product.name, price: product.price, sku: product.sku };
            vm.editError = null;
        };
        vm.closeEdit = function () {
            if (!vm.editSaving) { vm.editing = null; }
        };
        vm.update = function (form) {
            if (vm.editSaving || !vm.editing) { return; }
            vm.editError = null;
            if (form.$invalid || !vm.editInput.name || !vm.editInput.name.trim()
                    || !vm.editInput.sku || !vm.editInput.sku.trim()) {
                vm.editError = 'Informe nome, preço e SKU válidos.';
                return;
            }
            vm.editSaving = true;
            return productsService.update(vm.editing.id, vm.editInput).then(function () {
                vm.editing = null;
                vm.success = 'Produto atualizado com sucesso.';
                return vm.load();
            }, function (rejection) {
                vm.editError = productError(rejection, 'atualizar');
            }).finally(function () { vm.editSaving = false; });
        };
        vm.openDelete = function (product) {
            if (vm.canDelete()) {
                vm.deleting = product;
                vm.deleteError = null;
            }
        };
        vm.closeDelete = function () {
            if (!vm.deleteSaving) { vm.deleting = null; }
        };
        vm.remove = function () {
            if (vm.deleteSaving || !vm.deleting) { return; }
            vm.deleteSaving = true;
            vm.deleteError = null;
            return productsService.remove(vm.deleting.id).then(function () {
                vm.deleting = null;
                vm.success = 'Produto excluído com sucesso.';
                return vm.load();
            }, function (rejection) {
                vm.deleteError = productError(rejection, 'excluir');
            }).finally(function () { vm.deleteSaving = false; });
        };

        function productError(rejection, action) {
            if (rejection.status === 403) { return 'Você não tem permissão para ' + action + ' produtos.'; }
            if (rejection.status === 404) { return 'Produto não encontrado. Atualize a lista.'; }
            if (rejection.status === 400) { return 'Dados inválidos. Revise os campos do produto.'; }
            return 'Não foi possível ' + action + ' o produto. Tente novamente.';
        }

        vm.load();
    }
}());
