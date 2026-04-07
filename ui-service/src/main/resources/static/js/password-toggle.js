(function () {
    function initPasswordToggles() {
        const passwordInputs = document.querySelectorAll('input[type="password"]:not([data-password-toggle="attached"])');
        if (!passwordInputs.length) {
            return;
        }

        passwordInputs.forEach((input) => {
            if (!(input instanceof HTMLInputElement)) {
                return;
            }

            input.setAttribute('data-password-toggle', 'attached');
            input.classList.add('sh-password-input');

            const wrapper = document.createElement('div');
            wrapper.className = 'sh-password-wrap';

            const parent = input.parentNode;
            if (!parent) {
                return;
            }

            parent.insertBefore(wrapper, input);
            wrapper.appendChild(input);

            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'sh-password-toggle';
            button.setAttribute('aria-label', 'Mostrar contraseña');
            button.setAttribute('title', 'Mostrar contraseña');
            button.innerHTML = '<i class="fa-regular fa-eye"></i>';

            button.addEventListener('click', () => {
                const visible = input.type === 'text';
                input.type = visible ? 'password' : 'text';
                button.innerHTML = visible
                    ? '<i class="fa-regular fa-eye"></i>'
                    : '<i class="fa-regular fa-eye-slash"></i>';
                button.setAttribute('aria-label', visible ? 'Mostrar contraseña' : 'Ocultar contraseña');
                button.setAttribute('title', visible ? 'Mostrar contraseña' : 'Ocultar contraseña');
            });

            wrapper.appendChild(button);
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initPasswordToggles);
    } else {
        initPasswordToggles();
    }
})();
