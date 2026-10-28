import { expect, test } from '@playwright/test';
import { login } from './helpers';

test('quadros do cliente existem e a equipe monta colunas e cards', async ({ page, isMobile }) => {
  await login(page);
  await page.goto('/quadros');
  const tabs = page.getByRole('tablist', { name: 'Quadros' });
  await expect(tabs.getByRole('tab', { name: /Organização mensal/ })).toBeVisible();
  await expect(tabs.getByRole('tab', { name: /Licitações vencidas/ })).toBeVisible();

  // quadro próprio do teste
  const name = `E2E ${Date.now().toString(36)}`;
  await page.getByRole('button', { name: 'Novo quadro' }).click();
  await page.getByLabel('Nome do novo quadro').fill(name);
  await page.getByRole('button', { name: 'Criar', exact: true }).click();
  await expect(tabs.getByRole('tab', { name: new RegExp(name) })).toHaveAttribute('aria-selected', 'true');

  // quadro novo começa vazio e pede a primeira coluna
  await page.getByLabel('Comece criando a primeira coluna').fill('Novembro');
  await page.getByRole('button', { name: 'Criar coluna' }).click();
  await page.getByRole('button', { name: 'Nova coluna' }).click();
  await page.getByRole('textbox', { name: 'Nova coluna' }).fill('Dezembro');
  await page.getByRole('button', { name: 'Criar coluna' }).click();
  const nov = page.getByRole('region', { name: 'Novembro' });
  const dez = page.getByRole('region', { name: 'Dezembro' });
  await expect(dez).toBeVisible();

  // card rápido e depois os detalhes, ligando a licitação da SEFAZ
  await nov.getByRole('button', { name: 'Adicionar card' }).click();
  await nov.getByLabel('Novo card em Novembro').fill('Pedido SEFAZ de banners');
  await nov.getByRole('button', { name: 'Adicionar', exact: true }).click();
  await expect(nov.getByText('Pedido SEFAZ de banners')).toBeVisible();
  await nov.getByRole('button', { name: 'Fechar' }).click();

  await nov.getByRole('button', { name: /Pedido SEFAZ de banners/ }).click();
  const dialog = page.getByRole('dialog', { name: 'Card' });
  await dialog.getByLabel('Licitação ligada').fill('SEFAZ');
  await dialog.getByRole('button', { name: /SEFAZ · Secretaria de Fazenda/ }).first().click();
  await dialog.getByLabel('Prazo').fill('2026-01-10');
  await dialog.getByRole('button', { name: 'Salvar' }).click();
  await expect(dialog).toBeHidden();
  await expect(nov.getByText('SEFAZ · Secretaria de Fazenda')).toBeVisible();
  await expect(nov.getByText('R$ 237.000')).toHaveCount(2); // soma da coluna e valor no card
  await expect(nov.getByText('(atrasado)')).toBeAttached();

  // move para dezembro: arrastando no desktop, pelo campo "Coluna" no celular
  if (isMobile) {
    await nov.getByRole('button', { name: /Pedido SEFAZ de banners/ }).click();
    await dialog.getByLabel('Coluna').selectOption({ label: 'Dezembro' });
    await dialog.getByRole('button', { name: 'Salvar' }).click();
    // o foco acompanha o card na coluna nova
    await expect(dez.getByRole('button', { name: /Pedido SEFAZ de banners/ })).toBeFocused();
  } else {
    const card = nov.locator('.board-card').first();
    const from = await card.boundingBox();
    const to = await dez.locator('.column-body').boundingBox();
    await page.mouse.move(from!.x + 20, from!.y + 20);
    await page.mouse.down();
    await page.mouse.move(from!.x + 60, from!.y + 30, { steps: 5 });
    await page.mouse.move(to!.x + 40, to!.y + 30, { steps: 15 });
    await page.mouse.up();
  }
  await expect(dez.getByText('Pedido SEFAZ de banners')).toBeVisible();
  await expect(nov.getByText('Sem cards')).toBeVisible();
  await expect(dez.locator('.column-value')).toHaveText('R$ 237.000');

  // coluna com card não sai
  await dez.getByRole('button', { name: 'Excluir a coluna Dezembro' }).click();
  await dez.getByRole('button', { name: 'Confirmar exclusão' }).click();
  await expect(page.getByRole('alert')).toContainText('Mova ou apague antes de excluir');

  // limpeza: apaga o card e o quadro
  await dez.getByRole('button', { name: /Pedido SEFAZ de banners/ }).click();
  await dialog.getByRole('button', { name: 'Apagar' }).click();
  await dialog.getByRole('button', { name: 'Apagar' }).click();
  await expect(dez.getByText('Sem cards')).toBeVisible();
  await page.getByRole('button', { name: 'Apagar quadro' }).click();
  await expect(tabs.getByRole('tab', { name: new RegExp(name) })).toHaveCount(0);
});
