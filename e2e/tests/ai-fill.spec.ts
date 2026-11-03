import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';
import { login } from './helpers';

/**
 * Leitura da ata com IA (ADR 0016), com o leitor de demonstração do backend (SHEMA_EXTRACTION_DEMO=true): o fluxo
 * inteiro roda sem chave e sem custo. Licitações do seed vencidas e sem ata, uma por projeto, que nenhum outro teste
 * altera (Magé e MEC-INES).
 */
// Sem animação: o axe mede o contraste da tela parada
test.use({ reducedMotion: 'reduce' });

const TENDER_BY_PROJECT: Record<string, string> = {
  desktop: '20000000-0000-4000-8000-000000000003',
  phone: '20000000-0000-4000-8000-000000000005',
};

test('a IA lê o PDF da ata, preenche o formulário e os itens, e o histórico registra quem conferiu', async ({
  page,
}, info) => {
  test.setTimeout(90_000);
  await login(page);
  await page.goto(`/licitacoes/${TENDER_BY_PROJECT[info.project.name]}?aba=documentos`);
  await expect(page.getByText('Esta licitação ainda não tem ata.')).toBeVisible();

  // o PDF da ata, enviado como sempre
  await page.getByRole('button', { name: 'Enviar documento' }).click();
  const docDialog = page.getByRole('dialog', { name: 'Enviar documento' });
  await docDialog.getByLabel('Arquivo').setInputFiles({
    name: 'ata-045-2026.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.7\nata para a IA ler\n%%EOF'),
  });
  await docDialog.getByLabel('Título').fill('Ata 045/2026');
  await docDialog.getByRole('button', { name: 'Enviar' }).click();
  await expect(docDialog).toBeHidden();

  // Registrar ata → Ler com IA: os campos chegam preenchidos, com a página de origem e os avisos
  await page.getByRole('button', { name: 'Registrar ata' }).click();
  const ataDialog = page.getByRole('dialog', { name: 'Registrar ata' });
  await expect(ataDialog.getByRole('heading', { name: 'Preencher com IA' })).toBeVisible();
  await ataDialog.getByRole('button', { name: 'Ler com IA' }).click();
  await expect(ataDialog.getByText('Campos preenchidos com a leitura da IA.')).toBeVisible({
    timeout: 30_000,
  });
  await expect(ataDialog.getByLabel('Número da ata')).toHaveValue('ARP 045/2026');
  await expect(ataDialog.getByLabel('Assinada em')).toHaveValue('2026-06-02');
  await expect(ataDialog.getByLabel('Vigente até')).toHaveValue('2027-06-01');
  await expect(ataDialog.getByText('IA · p. 2')).toBeVisible();
  await expect(ataDialog.getByText(/A quantidade da faixa \(item 3\) está cortada/)).toBeVisible();
  await expect(ataDialog.getByLabel('Arquivo da ata')).toHaveValue(/.+/);
  // a caixa da IA, o resumo e as etiquetas passam na auditoria de acessibilidade (WCAG 2.1 AA)
  const axe = await new AxeBuilder({ page })
    .include('[role="dialog"]')
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();
  expect(axe.violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
  await ataDialog.getByRole('button', { name: 'Salvar' }).click();
  await expect(ataDialog).toBeHidden();
  const ata = page.locator('app-ata-section');
  await expect(ata.getByRole('heading', { name: 'Ata ARP 045/2026' })).toBeVisible();

  // os itens lidos abrem para conferir; o que a IA não leu fica em branco e a pessoa completa
  const items = page.getByRole('dialog', { name: 'Itens da ata' });
  await expect(items.getByText(/Itens lidos pela IA/)).toBeVisible();
  await expect(items.getByLabel('Descrição do item 1')).toHaveValue(
    'Banner em lona 440g com acabamento em bastão',
  );
  await expect(items.getByLabel('Valor unitário do item 2')).toHaveValue('38,75');
  await expect(items.getByLabel('Quantidade do item 3')).toHaveValue('');
  await items.getByLabel('Quantidade do item 3').fill('50');
  await items.getByRole('button', { name: 'Salvar itens' }).click();
  await expect(items).toBeHidden();
  await expect(ata.getByText('Faixa em lona 3 x 0,70 m')).toBeVisible();

  // o histórico guarda que os dados vieram da IA e quem conferiu
  await page.getByRole('tab', { name: 'Histórico' }).click();
  await expect(
    page
      .getByText(/Ata ARP 045\/2026 registrada.*dados sugeridos pela IA e conferidos por/)
      .first(),
  ).toBeVisible();
  await expect(page.getByText(/Itens da ata: 3 itens.*sugeridos pela IA/).first()).toBeVisible();

  // limpeza: ata e documento (apagar documento é lógico)
  await page.getByRole('tab', { name: 'Documentos' }).click();
  await ata.getByRole('button', { name: 'Editar' }).click();
  const edit = page.getByRole('dialog', { name: 'Editar ata' });
  await edit.getByRole('button', { name: 'Apagar' }).click();
  await edit.getByRole('button', { name: 'Apagar' }).click();
  await expect(page.getByText('Esta licitação ainda não tem ata.')).toBeVisible();
  // apaga o documento deste teste (e sobras de rodadas interrompidas, que têm o mesmo título)
  const docs = page.locator('app-documents-section');
  const mine = docs.locator('ul.docs > li').filter({ hasText: 'Ata 045/2026' });
  for (let left = await mine.count(); left > 0; left--) {
    await mine.first().getByRole('button', { name: 'Detalhes' }).click();
    await docs.getByRole('button', { name: 'Apagar', exact: true }).click();
    await docs.getByRole('button', { name: 'Apagar', exact: true }).click();
    await expect(mine).toHaveCount(left - 1);
  }
});
