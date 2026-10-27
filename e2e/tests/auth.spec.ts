import { expect, test } from '@playwright/test';
import { admin, login } from './helpers';

test('senha errada mostra o erro e mantém na tela de login', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('E-mail').fill('ninguem@shema.local');
  await page.getByLabel('Senha').fill('senha-errada');
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('alert')).toHaveText('E-mail ou senha não conferem.');
  await expect(page).toHaveURL(/\/login$/);
});

test('rota protegida sem sessão leva ao login', async ({ page }) => {
  await page.goto('/licitacoes');
  await expect(page).toHaveURL(/\/login$/);
});

test('login, sessão sobrevive ao recarregar e logout', async ({ page, isMobile }) => {
  await login(page);
  await page.reload();
  await expect(page.getByRole('heading', { level: 1, name: 'Painel' })).toBeVisible();

  if (isMobile) await page.getByRole('button', { name: 'Abrir menu' }).click();
  await page.getByRole('button', { name: 'Sair' }).click();
  await expect(page).toHaveURL(/\/login$/);
  await page.goto('/painel');
  await expect(page).toHaveURL(/\/login$/);
  expect(admin.email).toContain('@');
});
