(function () {
    'use strict';

    angular.module('pocKeycloakApp.auth')
        .controller('CompleteRegistrationController', CompleteRegistrationController);

    CompleteRegistrationController.$inject = ['authService', '$location'];

    function CompleteRegistrationController(authService, $location) {
        var vm = this;
        var pending = authService.getPendingRegistration();
        vm.pending = pending;
        vm.loading = false;
        vm.error = null;
        vm.success = null;
        // Only fields understood by CompleteRegistrationInput are rendered;
        // the response remains the source of truth for which ones are needed.
        var supportedFields = ['firstName', 'lastName', 'email'];
        vm.fields = pending ? supportedFields.filter(function (field) {
            return Object.prototype.hasOwnProperty.call(pending.missingFields, field);
        }) : [];
        vm.values = { firstName: '', lastName: '', email: '' };
        vm.labels = { firstName: 'Nome', lastName: 'Sobrenome', email: 'E-mail' };

        vm.submit = function (form) {
            if (vm.loading) return;
            vm.error = null;
            if (!pending || !pending.registrationTicket) {
                vm.error = 'A sessão de conclusão cadastral expirou. Faça login novamente.';
                return;
            }
            if (form.$invalid) {
                vm.error = 'Preencha os campos obrigatórios para continuar.';
                return;
            }
            for (var i = 0; i < vm.fields.length; i += 1) {
                if (!vm.values[vm.fields[i]] || !vm.values[vm.fields[i]].trim()) {
                    vm.error = 'Preencha os campos obrigatórios para continuar.';
                    return;
                }
            }
            vm.loading = true;
            return authService.completeRegistration({
                username: pending.username,
                registration_ticket: pending.registrationTicket,
                firstName: vm.values.firstName,
                lastName: vm.values.lastName,
                email: vm.values.email
            }).then(function () {
                authService.clearPendingRegistration();
                vm.success = 'Cadastro concluído. Faça login novamente para continuar.';
                $location.path('/login');
            }, function (rejection) {
                vm.error = rejection.data && rejection.data.message
                    ? rejection.data.message : 'Não foi possível concluir o cadastro.';
            }).finally(function () { vm.loading = false; });
        };
    }
}());
