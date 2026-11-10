import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { login } from './helpers';

/**
 * "Pergunte ao LicitaOne" (Fase 11) com o assistente de demonstração do backend (SHEMA_EXTRACTION_DEMO=true): escolhe
 * a consulta pela palavra-chave e responde com os itens que ela devolveu, pelo mesmo laço do modelo real. Só lê, então
 * os dois projetos rodam juntos. Rode o compose com SHEMA_ASSISTANT_DAILY_LIMIT alto (o E2E pergunta várias vezes).
 */
test.use({ reducedMotion: 'reduce' });

test('pergunta sobre a carteira, segue a conversa e abre um item da lista', async ({ page }) => {
  test.setTimeout(90_000);
  await login(page);
  await page.goto('/painel');
  await page.getByRole('button', { name: 'Pergunte ao LicitaOne' }).click();

  const panel = page.getByRole('dialog', { name: 'Pergunte ao LicitaOne' });
  await expect(panel.getByText('Exemplos')).toBeVisible();
  await expect(panel.getByText(/perguntas hoje/)).toBeVisible();

  // Um exemplo: contratos com saldo
  await panel.getByRole('button', { name: /mais de R\$ 100 mil de saldo/ }).click();
  await expect(panel.getByText(/Encontrei \d+ resultados?/)).toBeVisible({ timeout: 30_000 });
  const items = panel.getByRole('list', { name: /Itens da resposta/ });
  await expect(items.getByRole('button').first()).toBeVisible();
  await expect(items.getByText('Contrato').first()).toBeVisible();

  await panel.getByRole('button', { name: 'Ver as consultas feitas' }).click();
  await expect(panel.getByText(/Contratos abertos/)).toBeVisible();

  // Pergunta de seguimento, digitada
  await panel.getByLabel('Sua pergunta').fill('E quais atas ainda aceitam carona?');
  await panel.getByRole('button', { name: 'Perguntar' }).click();
  await expect(panel.getByText(/Encontrei \d+ resultados?/)).toHaveCount(2, { timeout: 30_000 });
  await expect(panel.getByText('Ata', { exact: true }).first()).toBeVisible();

  const axe = await new AxeBuilder({ page })
    .include('[role="dialog"]')
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  expect(axe.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);

  // Abrir um item leva à licitação e fecha o painel (a conversa some)
  await items.getByRole('button').first().click();
  await expect(panel).toBeHidden();
  await expect(page).toHaveURL(/\/licitacoes\/[0-9a-f-]+\?aba=itens/);
  await page.getByRole('button', { name: 'Pergunte ao LicitaOne' }).click();
  await expect(panel.getByText('Exemplos')).toBeVisible();
});
