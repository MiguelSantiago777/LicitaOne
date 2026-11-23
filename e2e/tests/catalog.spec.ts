import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { login } from './helpers';

/** Desktop e celular rodam em paralelo: cada projeto cria os próprios registros, com sufixo único. */
test('nicho, fornecedor e produto: preço de cada fornecedor, menor preço e filtro por nicho', async ({
  page,
}, info) => {
  test.setTimeout(120_000);
  const tag = `${info.project.name}-${Date.now().toString(36)}`;
  await login(page);
  await page.goto('/fornecedores-produtos');
  await expect(page.getByRole('heading', { level: 1, name: 'Fornecedores e produtos' })).toBeVisible();

  // Nicho novo
  await page.getByRole('tab', { name: /Nichos/ }).click();
  await page.getByRole('button', { name: 'Novo nicho' }).click();
  const niche = `Copa E2E ${tag}`;
  await page.getByLabel('Nome do nicho').fill(niche);
  await page.getByRole('button', { name: 'Criar nicho' }).click();
  await expect(page.getByRole('cell', { name: niche, exact: true })).toBeVisible();

  // Dois fornecedores no nicho
  const suppliers = [`Fornecedor Caro ${tag}`, `Fornecedor Barato ${tag}`];
  for (const name of suppliers) {
    await page.goto('/fornecedores-produtos?aba=fornecedores');
    await page.getByRole('button', { name: 'Novo fornecedor' }).click();
    await page.getByLabel('Razão social').fill(name);
    await page.getByLabel(/^Nicho/).selectOption({ label: niche });
    await page.getByRole('button', { name: 'Cadastrar e abrir' }).click();
    await expect(page.getByRole('heading', { level: 1, name })).toBeVisible();
    await expect(page.getByRole('checkbox', { name: niche })).toBeChecked();
  }
  // Contato e CNPJ no último, salvando sozinho
  await page.getByLabel('CNPJ').fill('12345678000195');
  await page.getByLabel('CNPJ').blur();
  await expect(page.getByLabel('CNPJ')).toHaveValue('12.345.678/0001-95');
  await page.getByRole('button', { name: 'Adicionar contato' }).click();
  await page.getByLabel('Nome', { exact: true }).fill('Vendas');
  await page.getByLabel('Telefone').fill('(21) 99999-0000');
  await expect(page.getByRole('status').filter({ hasText: 'Salvo' })).toBeVisible();

  // Produto com os dois preços
  await page.goto('/fornecedores-produtos');
  await page.getByRole('button', { name: 'Novo produto' }).click();
  const product = `Copo descartável ${tag}`;
  await page.getByLabel('Nome do produto').fill(product);
  await page.getByLabel('Nicho', { exact: true }).selectOption({ label: niche });
  await page.getByRole('button', { name: 'Cadastrar e abrir' }).click();
  await expect(page.getByRole('heading', { level: 1, name: product })).toBeVisible();
  await page.getByLabel('Unidade').fill('pct');

  const prices = ['12,90', '9,75'];
  for (let i = 0; i < suppliers.length; i++) {
    await page.getByRole('button', { name: 'Adicionar fornecedor' }).click();
    await page.getByLabel('Fornecedor', { exact: true }).nth(i).selectOption({ label: suppliers[i] });
    await page.getByLabel('Preço de custo (R$)').nth(i).fill(prices[i]);
  }
  await page.getByLabel('Preço de custo (R$)').nth(1).blur();
  await expect(page.getByRole('status').filter({ hasText: 'Salvo' })).toBeVisible();
  await expect(page.getByText('Menor preço')).toHaveCount(1);
  // O servidor pôs a data de hoje nas cotações novas
  await expect(page.getByLabel('Data da cotação').first()).not.toHaveValue('');

  const audit = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  expect(audit.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target).join(' | ')}`)).toEqual([]);

  // Na lista, filtrada pelo nicho: o menor preço e de quem é
  await page.goto('/fornecedores-produtos');
  await page.getByLabel('Filtrar por nicho').selectOption({ label: niche });
  const row = page.getByRole('row', { name: new RegExp(product) });
  await expect(row).toBeVisible();
  await expect(row).toContainText('R$ 9,75');
  await expect(row).toContainText(suppliers[1]);
  await expect(page.getByRole('row')).toHaveCount(2);

  // Na aba Nichos, as contagens
  await page.getByRole('tab', { name: /Nichos/ }).click();
  const nicheRow = page.getByRole('row', { name: new RegExp(niche) });
  await expect(nicheRow.getByRole('button', { name: /2 fornecedores/ })).toBeVisible();
  await expect(nicheRow.getByRole('button', { name: /1 produtos/ })).toBeVisible();

  // No fornecedor mais barato, o produto aparece como menor preço
  await page.goto('/fornecedores-produtos?aba=fornecedores');
  await page.getByRole('link', { name: suppliers[1] }).click();
  const offered = page.getByRole('row', { name: new RegExp(product) });
  await expect(offered).toContainText('Menor preço');
});
