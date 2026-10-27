import { expect, Page } from '@playwright/test';

export const admin = {
  email: process.env.SHEMA_ADMIN_EMAIL ?? 'admin@shema.local',
  password: process.env.SHEMA_ADMIN_PASSWORD ?? '',
};

export async function login(page: Page, email = admin.email, password = admin.password) {
  await page.goto('/login');
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Senha').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await expect(page.getByRole('heading', { level: 1, name: 'Painel' })).toBeVisible();
}
