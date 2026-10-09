(function () {
    'use strict';

    angular.module('pocKeycloakApp.auth')
        .controller('LoginController', LoginController);

    LoginController.$inject = ['authService', '$location'];

    function LoginController(authService, $location) {
        var vm = this;
        vm.credentials = { username: '', password: '' };
        vm.loading = false;
        vm.error = null;
        vm.submit = function (form) {
            if (vm.loading) {
                return;
            }
            vm.error = null;
            if (form.$invalid || !vm.credentials.username.trim()) {
                vm.error = 'Informe o usuário e a senha.';
                return;
            }
            vm.loading = true;
            return authService.login(vm.credentials).then(function () {
                $location.path('/products');
            }, function (rejection) {
                if (rejection.status === 403 && rejection.data
                        && rejection.data.status === 'PENDING_REGISTRATION') {
                    authService.savePendingRegistration(rejection, vm.credentials.username.trim());
                    $location.path('/complete-registration');
                } else if (rejection.status === 401) {
                    vm.error = 'Usuário ou senha inválidos.';
                } else if (rejection.status === 400) {
                    vm.error = 'Informe o usuario e a senha.';
                } else {
                    vm.error = 'Não foi possível entrar. Tente novamente.';
                }
            }).finally(function () {
                vm.credentials.password = '';
                vm.loading = false;
            });
        };
    }
}());
