import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('cadastra licitação vencida, registra assinatura e vê o histórico', async ({ page }) => {
  const processNumber = `${Math.floor(Math.random() * 900) + 100}/2026`;
  await login(page);
  await page.goto('/licitacoes');
  await page.getByRole('main').getByRole('button', { name: 'Nova licitação' }).click();

  const drawer = page.getByRole('dialog', { name: 'Nova licitação' });
  await drawer.getByPlaceholder('Buscar órgão por nome ou município').fill('Petró');
  await drawer.getByRole('option', { name: /Prefeitura Municipal de Petrópolis/ }).click();
  await drawer.getByLabel('Nº do pregão / processo').fill(processNumber);
  await drawer.getByLabel('Objeto (descritivo da licitação)').fill('Banners e adesivos perfurados para campanha');
  await drawer.getByLabel('Tipo de material').fill('Material gráfico');
  await drawer.getByLabel('Valor total').fill('123.456,78');
  await drawer.getByRole('button', { name: 'Cadastrar licitação' }).click();

  await expect(page.getByRole('heading', { level: 1, name: 'Prefeitura Municipal de Petrópolis' })).toBeVisible();
  await expect(page.getByText(`PE ${processNumber.padStart(8, '0')}`)).toBeVisible();
  await expect(page.locator('.head .badge')).toHaveText('Vencida');

  await page.getByRole('button', { name: 'Registrar assinatura' }).click();
  const dialog = page.getByRole('dialog', { name: 'Registrar assinatura' });
  await expect(dialog.getByLabel('Data de assinatura')).toBeVisible();
  await dialog.getByRole('button', { name: 'Confirmar' }).click();
  await expect(page.locator('.head .badge')).toHaveText('Contrato assinado');

  await page.getByRole('tab', { name: 'Histórico' }).click();
  await expect(page.getByText('mudou de Vencida para Contrato assinado')).toBeVisible();
  await expect(page.getByText('cadastrou a licitação como Vencida')).toBeVisible();
});

test('assinar sem data não é aceito e a mensagem explica o porquê', async ({ page }) => {
  await login(page);
  await page.goto('/licitacoes?aba=vencidas&q=Cantagalo');
  await page.getByRole('link', { name: 'Prefeitura Municipal de Cantagalo' }).click();
  await page.getByRole('button', { name: 'Registrar assinatura' }).click();
  const dialog = page.getByRole('dialog', { name: 'Registrar assinatura' });
  await dialog.getByLabel('Data de assinatura').fill('');
  await dialog.getByRole('button', { name: 'Confirmar' }).click();
  await expect(dialog.getByRole('alert')).toContainText('Informe a data de assinatura');
});

test('busca e filtros ficam na URL e mudam os contadores', async ({ page }) => {
  await login(page);
  await page.goto('/licitacoes?aba=vencidas');
  await page.getByPlaceholder('Órgão, município, UASG, pregão ou material').fill('Mesquita');
  await expect(page).toHaveURL(/q=Mesquita/);
  await expect(page.getByRole('link', { name: 'Prefeitura Municipal de Mesquita' })).toBeVisible();
  await expect(page.locator('tbody tr')).toHaveCount(1);
  await expect(page.getByRole('tab', { name: /Vencidas/ }).locator('.count')).toHaveText('1');

  await page.reload();
  await expect(page.getByRole('link', { name: 'Prefeitura Municipal de Mesquita' })).toBeVisible();
});
