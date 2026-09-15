document.addEventListener('DOMContentLoaded', () => {
  const current = api.session();
  if (current?.user?.role === 'ADMIN') location.replace('admin.html');
  if (current?.user?.role === 'DRIVER') location.replace('driver.html');

  const form = document.querySelector('#login-form');
  const message = document.querySelector('#login-message');
  form.addEventListener('submit', async event => {
    event.preventDefault();
    pp.hideMessage(message);
    const payload = {
      username: form.username.value.trim(),
      password: form.password.value
    };
    try {
      const response = await api.postJson('/api/v1/auth/login', payload, { auth: false });
      api.saveSession(response);
      location.replace(response.user.role === 'ADMIN' ? 'admin.html' : 'driver.html');
    } catch (error) {
      pp.showMessage(message, error.message || 'Não foi possível iniciar sessão.');
    }
  });
});
