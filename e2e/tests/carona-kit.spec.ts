import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { login } from './helpers';

/**
 * Kit de carona (Fase 10) com o leitor de demonstração do backend (SHEMA_EXTRACTION_DEMO=true). Usa a ata da SEFAZ do
 * seed (ARP 017/2026) e oferece a Petrópolis; nada é gravado, então os dois projetos podem rodar juntos.
 */
test.use({ reducedMotion: 'reduce' });

const SEFAZ_TENDER = '20000000-0000-4000-8000-000000000013';

test('o kit de carona sai com quanto o município pode pedir, sem IA e com IA, e baixa em .docx', async ({
  page,
}) => {
  test.setTimeout(90_000);
  await login(page);
  await page.goto(`/licitacoes/${SEFAZ_TENDER}?aba=documentos`);
  await page.getByRole('button', { name: 'Kit de carona' }).click();

  const kit = page.getByRole('dialog', { name: 'Kit de carona' });
  await kit.getByPlaceholder('Buscar órgão por nome ou município').fill('Petró');
  await kit.getByRole('option', { name: /Prefeitura Municipal de Petrópolis/ }).click();
  await expect(kit.getByText(/de cada item por município/)).toBeVisible();
  await expect(kit.getByText(/pode até/).first()).toBeVisible();

  // Sem IA: os três textos saem dos modelos fixos
  await kit.getByRole('button', { name: 'Gerar sem IA' }).click();
  await expect(kit.getByRole('tab', { name: 'Carta de apresentação' })).toHaveAttribute(
    'aria-selected',
    'true',
  );
  await expect(kit.getByLabel('Assunto')).toHaveValue(
    'Adesão à Ata de Registro de Preços nº ARP 017/2026',
  );
  await kit.getByRole('tab', { name: 'Modelo de ofício de solicitação' }).click();
  await expect(kit.getByLabel('Texto', { exact: true })).toHaveValue(
    /art\. 86, § 2º, da Lei nº 14\.133\/2021/,
  );
  await kit.getByRole('tab', { name: 'Ofício de anuência' }).click();
  await expect(kit.getByLabel('Texto', { exact: true })).toHaveValue(/^OFÍCIO DE ANUÊNCIA/);
  await expect(kit.getByLabel('Texto', { exact: true })).toHaveValue(
    /Prefeitura Municipal de Petrópolis/,
  );

  const download = page.waitForEvent('download');
  await kit.getByRole('button', { name: 'Baixar .docx' }).click();
  expect((await download).suggestedFilename()).toMatch(/^Anuência - ARP 017-2026 - .*\.docx$/);

  // Com IA: a carta e a justificativa vêm do leitor de demonstração; a anuência continua fixa
  await kit.getByRole('button', { name: 'Gerar com IA' }).click();
  await expect(kit.getByText('Escrito com IA')).toBeVisible({
    timeout: 30_000,
  });
  await expect(kit.getByLabel('Assunto')).toHaveValue(
    'Proposta de adesão à ata de registro de preços',
  );
  await expect(kit.getByText('Leitor de demonstração: texto fixo.')).toBeVisible();
  await expect(kit.getByRole('link', { name: 'Abrir no e-mail' })).toHaveAttribute(
    'href',
    /^mailto:/,
  );

  const axe = await new AxeBuilder({ page })
    .include('[role="dialog"]')
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  expect(axe.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
});
