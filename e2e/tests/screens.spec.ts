import { expect, test } from '@playwright/test';
import { login } from './helpers';

const screens = [
  { path: '/painel', title: 'Painel' },
  { path: '/licitacoes', title: 'Licitações' },
  { path: '/atas', title: 'Atas' },
  { path: '/negociacoes', title: 'Negociações' },
  { path: '/tarefas', title: 'Tarefas' },
  { path: '/importacao', title: 'Importação' },
];

test('@screens login', async ({ page }, info) => {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible();
  await page.screenshot({ path: `screenshots/${info.project.name}/00-login.png`, fullPage: true });
});

test('@screens telas principais', async ({ page, isMobile }, info) => {
  await login(page);
  for (const [index, screen] of screens.entries()) {
    if (isMobile) {
      await page.goto(screen.path);
    } else {
      await page
        .getByRole('navigation', { name: 'Navegação principal' })
        .getByRole('link', { name: screen.title })
        .click();
    }
    await expect(page.getByRole('heading', { level: 1, name: screen.title })).toBeVisible();
    const name = `${String(index + 1).padStart(2, '0')}-${screen.path.slice(1)}`;
    await page.screenshot({ path: `screenshots/${info.project.name}/${name}.png`, fullPage: true });
  }
  if (isMobile) {
    await page.getByRole('button', { name: 'Abrir menu' }).click();
    const nav = page.getByRole('navigation', { name: 'Navegação principal' });
    await expect(nav).toHaveCSS('transform', 'none');
    await page.screenshot({ path: `screenshots/${info.project.name}/99-menu.png` });
  }
});
