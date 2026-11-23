import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { login } from './helpers';

// Sem animação: o axe mede o contraste da tela parada (o selo do menu entra com animação)
test.use({ reducedMotion: 'reduce' });

test('relatórios: cartões, gráficos, tabela, filtros na URL, troca de número e PDF', async ({ page }, info) => {
  test.setTimeout(90_000);
  const year = new Date().getFullYear();
  await login(page);
  await page.goto('/relatorios');
  await expect(page.getByRole('heading', { level: 1, name: 'Relatórios' })).toBeVisible();

  // Pedidos é o padrão: cartões e os dois gráficos
  await expect(page.getByRole('tab', { name: 'Pedidos recebidos', selected: true })).toBeVisible();
  const summary = page.getByRole('region', { name: 'Resumo do ano' });
  await expect(summary.getByText(`Total em ${year}`)).toBeVisible();
  await expect(summary.getByText('Melhor mês')).toBeVisible();
  await expect(page.getByRole('img', { name: /Colunas mês a mês/ })).toBeVisible();
  await expect(page.getByRole('img', { name: /Acumulado de/ })).toBeVisible();

  // A tabela mês a mês fecha com o total do cartão
  await page.getByText('Ver tabela mês a mês').click();
  const table = page.getByRole('table', { name: /por mês em/ });
  await expect(table.getByRole('row')).toHaveCount(14);
  const total = (await summary.locator('.kpi-value').first().textContent())!.trim();
  await expect(table.locator('tfoot')).toContainText(total);

  await page.screenshot({ path: `screenshots/relatorios-${info.project.name}.png`, fullPage: true });

  await page.waitForLoadState('networkidle');
  const audit = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  expect(audit.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target).join(' | ')}`)).toEqual([]);

  // Filtro por UF vai para a URL e muda o subtítulo
  await page.getByLabel('UF', { exact: true }).selectOption('RJ');
  await expect(page).toHaveURL(/uf=RJ/);
  await expect(page.getByText(/· UF RJ/)).toBeVisible();

  // Outro número: licitações vencidas tem a quebra por modalidade
  await page.getByRole('tab', { name: 'Licitações vencidas' }).click();
  await expect(page).toHaveURL(/numero=LICITACOES/);
  await expect(page.getByRole('heading', { name: 'Por modalidade' })).toBeVisible();
  // Caronas não tem quebra por tipo
  await page.getByRole('tab', { name: 'Caronas' }).click();
  await expect(page.getByRole('heading', { name: 'Órgãos que mais compraram' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Por modalidade' })).toHaveCount(0);

  // PDF com o nome do número, do ano e da UF
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Baixar PDF' }).click();
  expect((await download).suggestedFilename()).toBe(`Relatorio-caronas-${year}-RJ.pdf`);
});
