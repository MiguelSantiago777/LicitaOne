import { expect, test } from '@playwright/test';
import { login } from './helpers';

const screens = [
  { path: '/painel', title: 'Painel' },
  { path: '/licitacoes', title: 'Licitações' },
  { path: '/orgaos', title: 'Órgãos' },
  { path: '/atas', title: 'Atas' },
  { path: '/quadros', title: 'Quadros' },
  { path: '/negociacoes', title: 'Negociações' },
  { path: '/tarefas', title: 'Tarefas' },
  { path: '/importacao', title: 'Importação' },
  { path: '/usuarios', title: 'Usuários' },
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
    await page.waitForLoadState('networkidle'); // dados da tela carregados antes da captura
    const name = `${String(index + 1).padStart(2, '0')}-${screen.path.slice(1)}`;
    await page.screenshot({ path: `screenshots/${info.project.name}/${name}.png`, fullPage: true });
  }
  await page.goto('/licitacoes?aba=vencidas');
  await page.locator('tbody tr a').first().click();
  await expect(page.getByRole('tab', { name: 'Histórico' })).toBeVisible();
  await page.screenshot({ path: `screenshots/${info.project.name}/20-licitacao-detalhe.png`, fullPage: true });
  // Contrato em andamento do seed (SEFAZ): itens, pedidos e saldo
  await page.goto('/licitacoes?aba=andamento&q=SEFAZ');
  await page.locator('tbody tr a').first().click();
  await page.getByRole('tab', { name: 'Itens e pedidos' }).click();
  await expect(page.locator('section.contract')).toBeVisible();
  await page.screenshot({ path: `screenshots/${info.project.name}/22-itens-e-pedidos.png`, fullPage: true });
  await page.getByRole('button', { name: 'Registrar pedido' }).click();
  await expect(page.getByRole('dialog', { name: 'Registrar pedido' })).toBeVisible();
  await page.waitForTimeout(300); // fim da animação da gaveta
  await page.screenshot({ path: `screenshots/${info.project.name}/23-registrar-pedido.png` });
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Importar planilha' }).click();
  await expect(page.getByRole('dialog', { name: 'Importar planilha' })).toBeVisible();
  await page.waitForTimeout(300);
  await page.screenshot({ path: `screenshots/${info.project.name}/24-importar-planilha.png` });
  await page.keyboard.press('Escape');
  await page.goto('/licitacoes');
  await page.getByRole('main').getByRole('button', { name: 'Nova licitação' }).click();
  await expect(page.getByRole('dialog', { name: 'Nova licitação' })).toBeVisible();
  await page.screenshot({ path: `screenshots/${info.project.name}/21-nova-licitacao.png` });
  await page.keyboard.press('Escape');
  if (isMobile) {
    await page.getByRole('button', { name: 'Abrir menu' }).click();
    const nav = page.getByRole('navigation', { name: 'Navegação principal' });
    await expect(nav).toHaveCSS('transform', 'none');
    await page.screenshot({ path: `screenshots/${info.project.name}/99-menu.png` });
  }
});
