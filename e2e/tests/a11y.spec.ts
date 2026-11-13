import AxeBuilder from '@axe-core/playwright';
import { expect, Page, test } from '@playwright/test';
import { login } from './helpers';

/**
 * Auditoria de acessibilidade (Fase 6): axe com as regras WCAG 2.1 A e AA em todas as telas e nos diálogos
 * principais, em 1440 e 390 px. Qualquer violação quebra o build.
 */

// Sem animação: o axe mede o contraste da tela parada, não de um texto no meio do fade de entrada
test.use({ reducedMotion: 'reduce' });
async function audit(page: Page, name: string): Promise<void> {
  await page.waitForLoadState('networkidle');
  const result = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  const report = result.violations.map(
    (v) =>
      `${v.id} (${v.impact}): ${v.help}\n` +
      v.nodes
        .slice(0, 5)
        .map((n) => `    ${n.target.join(' ')} → ${n.failureSummary?.split('\n').slice(1).join(' ')}`)
        .join('\n'),
  );
  // Soft: a rodada mostra as violações de todas as telas, não só da primeira
  expect.soft(report, `${name}\n${report.join('\n')}`).toEqual([]);
}

const pages: { path: string; heading: string }[] = [
  { path: '/painel', heading: 'Painel' },
  { path: '/licitacoes', heading: 'Licitações' },
  { path: '/orgaos', heading: 'Órgãos' },
  { path: '/atas', heading: 'Atas' },
  { path: '/atas?aba=MUNICIPIOS', heading: 'Atas' },
  { path: '/quadros', heading: 'Quadros' },
  { path: '/negociacoes', heading: 'Negociações' },
  { path: '/negociacoes?visao=lista', heading: 'Negociações' },
  { path: '/propostas', heading: 'Propostas de preços' },
  { path: '/tarefas', heading: 'Tarefas' },
  { path: '/alertas', heading: 'Alertas' },
  { path: '/importacao', heading: 'Importação' },
  { path: '/usuarios', heading: 'Usuários' },
];

test('login sem violações de acessibilidade', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Entrar' })).toBeVisible();
  await audit(page, '/login');
});

test('telas principais sem violações de acessibilidade', async ({ page }) => {
  test.setTimeout(90_000);
  await login(page);
  for (const p of pages) {
    await page.goto(p.path);
    await expect(page.getByRole('heading', { level: 1, name: p.heading })).toBeVisible();
    await audit(page, p.path);
  }
});

test('detalhe da licitação, abas e diálogos sem violações de acessibilidade', async ({ page }) => {
  test.setTimeout(90_000);
  await login(page);
  // Contrato em andamento do seed (SEFAZ): tem itens, pedidos, ata, documentos e tarefas
  await page.goto('/licitacoes/20000000-0000-4000-8000-000000000013');
  await expect(page.getByRole('tab', { name: 'Histórico' })).toBeVisible();
  for (const tab of ['Resumo', 'Itens e pedidos', 'Documentos', 'Histórico', 'Tarefas', 'Negociação']) {
    await page.getByRole('tab', { name: tab }).click();
    await audit(page, `detalhe · ${tab}`);
  }
  await page.getByRole('tab', { name: 'Itens e pedidos' }).click();
  await page.getByRole('button', { name: 'Registrar pedido' }).click();
  await expect(page.getByRole('dialog', { name: 'Registrar pedido' })).toBeVisible();
  await audit(page, 'diálogo Registrar pedido');
  await page.keyboard.press('Escape');

  await page.getByRole('tab', { name: 'Documentos' }).click();
  await page.locator('app-ata-section').getByRole('button', { name: 'Registrar carona' }).click();
  await expect(page.getByRole('dialog', { name: 'Registrar carona' })).toBeVisible();
  await audit(page, 'diálogo Registrar carona');
  await page.keyboard.press('Escape');

  await page.goto('/licitacoes');
  await page.getByRole('main').getByRole('button', { name: 'Nova licitação' }).click();
  await expect(page.getByRole('dialog', { name: 'Nova licitação' })).toBeVisible();
  await audit(page, 'diálogo Nova licitação');
  await page.keyboard.press('Escape');

  await page.goto('/tarefas?q=Sem%20pedido&situacao=TODAS');
  await page.getByRole('button', { name: /^Sem pedido há/ }).first().click();
  await expect(page.getByRole('dialog', { name: 'Tarefa' })).toBeVisible();
  await audit(page, 'diálogo Tarefa com rascunho');
  await page.keyboard.press('Escape');

  await page.goto('/negociacoes?visao=lista');
  await page.getByRole('button', { name: 'Carona da ata SEFAZ · material gráfico' }).click();
  await expect(page.getByRole('dialog', { name: 'Negociação' })).toBeVisible();
  await audit(page, 'diálogo Negociação');
});
