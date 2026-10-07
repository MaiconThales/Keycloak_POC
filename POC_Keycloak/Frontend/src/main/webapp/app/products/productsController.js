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
        vm.listError = null;
        vm.createError = null;
        vm.success = null;

        vm.isAuthenticated = function () {
            return !!authService.getAccessToken();
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

        vm.load();
    }
}());
