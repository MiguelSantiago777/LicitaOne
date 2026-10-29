import { expect, test } from '@playwright/test';
import { login } from './helpers';

/**
 * Licitações do seed vencidas e sem ata, uma por projeto: desktop e celular rodam ao mesmo tempo e cada um mexe na sua
 * (Conceição de Macabu e Comando da 1ª Região Militar).
 */
const TENDER_BY_PROJECT: Record<string, string> = {
  desktop: '20000000-0000-4000-8000-000000000009',
  phone: '20000000-0000-4000-8000-000000000006',
};

const pdf = (text: string) => ({
  name: 'Ata 022-2026.pdf',
  mimeType: 'application/pdf',
  buffer: Buffer.from(`%PDF-1.7\n${text}\n%%EOF`),
});

test('documento, ata, itens e caronas com limite sinalizado; a ata vigente aparece como oportunidade', async ({
  page,
}, info) => {
  await login(page);
  await page.goto(`/licitacoes/${TENDER_BY_PROJECT[info.project.name]}?aba=documentos`);
  await expect(page.getByRole('tab', { name: 'Documentos' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByText('Esta licitação ainda não tem ata.')).toBeVisible();

  // envia o PDF da ata; um arquivo de outro tipo é recusado pelo servidor
  await page.getByRole('button', { name: 'Enviar documento' }).click();
  const docDialog = page.getByRole('dialog', { name: 'Enviar documento' });
  await docDialog.getByLabel('Arquivo').setInputFiles({
    name: 'ata.pdf',
    mimeType: 'application/pdf',
    buffer: Buffer.from('MZ isto não é um PDF'),
  });
  await docDialog.getByRole('button', { name: 'Enviar' }).click();
  await expect(docDialog.getByRole('alert')).toContainText('não é de um .pdf');
  await docDialog.getByLabel('Arquivo').setInputFiles(pdf('v1'));
  await expect(docDialog.getByLabel('Título')).toHaveValue('ata');
  await docDialog.getByLabel('Título').fill('Ata 022/2026');
  await docDialog.getByRole('button', { name: 'Enviar' }).click();
  await expect(docDialog).toBeHidden();
  const docs = page.locator('app-documents-section');
  await expect(docs.getByText('Ata 022/2026', { exact: true })).toBeVisible();
  const download = page.waitForEvent('download');
  await docs.getByRole('button', { name: 'Baixar Ata 022/2026' }).click();
  expect((await download).suggestedFilename()).toBe('Ata 022-2026.pdf');

  // registra a ata ligada ao documento
  await page.getByRole('button', { name: 'Registrar ata' }).click();
  const ataDialog = page.getByRole('dialog', { name: 'Registrar ata' });
  await ataDialog.getByLabel('Número da ata').fill('ARP 022/2026');
  await ataDialog.getByLabel('Assinada em').fill('2026-10-25');
  const nextYear = new Date().getFullYear() + 1;
  await ataDialog.getByLabel('Vigente até').fill(`${nextYear}-10-25`);
  await ataDialog.getByLabel('Arquivo da ata').selectOption({ label: 'Ata · Ata 022/2026' });
  await ataDialog.getByRole('button', { name: 'Salvar' }).click();
  await expect(ataDialog).toBeHidden();
  const ata = page.locator('app-ata-section');
  await expect(ata.getByRole('heading', { name: 'Ata ARP 022/2026' })).toBeVisible();
  await expect(ata.getByText('Vigente', { exact: true })).toBeVisible();
  // nas outras abas, o cabeçalho destaca a oportunidade e leva de volta à ata
  await page.getByRole('tab', { name: 'Resumo' }).click();
  await expect(page.locator('.ata-highlight')).toContainText(`25/10/${nextYear}`);
  await page.locator('.ata-highlight').getByRole('button', { name: 'Ver ata' }).click();
  await expect(page.getByRole('tab', { name: 'Documentos' })).toHaveAttribute('aria-selected', 'true');

  // itens registrados
  await ata.getByRole('button', { name: 'Itens', exact: true }).click();
  const items = page.getByRole('dialog', { name: 'Itens da ata' });
  await items.getByLabel('Descrição do item 1').fill('Kit escolar fundamental');
  await items.getByLabel('Quantidade do item 1').fill('100');
  await items.getByLabel('Valor unitário do item 1').fill('42,60');
  await expect(items.getByText('R$ 4.260,00')).toBeVisible();
  await items.getByRole('button', { name: 'Salvar itens' }).click();
  await expect(items).toBeHidden();
  await expect(ata.getByText('0 de 200')).toBeVisible();

  // carona dentro do limite
  await ata.getByRole('button', { name: 'Registrar carona' }).click();
  const carona = page.getByRole('dialog', { name: 'Registrar carona' });
  // órgãos estaduais: nunca aparecem em "municípios sem ata", que o outro teste confere ao mesmo tempo
  await carona.getByLabel('Órgão que aderiu').fill('SEGOV');
  await carona.getByRole('option', { name: /SEGOV · Secretaria de Governo/ }).click();
  await carona.getByLabel(/Kit escolar fundamental/).fill('150');
  await expect(carona.getByText('Dentro dos limites.')).toBeVisible();
  await expect(carona.getByText('R$ 6.390,00')).toBeVisible();
  await carona.getByRole('button', { name: 'Registrar carona' }).click();
  await expect(carona).toBeHidden();
  await expect(ata.getByText('SEGOV · Secretaria de Governo')).toBeVisible();

  // passa de 200% do item: só registra marcando "registrar mesmo assim"
  await ata.getByRole('button', { name: 'Registrar carona' }).click();
  await carona.getByLabel('Órgão que aderiu').fill('Casa Civil');
  await carona.getByRole('option', { name: /Casa Civil/ }).click();
  await carona.getByLabel(/Kit escolar fundamental/).fill('100');
  await expect(carona.getByText(/acima de 200% do registrado/)).toBeVisible();
  const register = carona.getByRole('button', { name: 'Registrar carona' });
  await expect(register).toBeDisabled();
  await carona.getByLabel(/Registrar mesmo assim/).check();
  await register.click();
  await expect(carona).toBeHidden();
  await expect(ata.getByText('Passou dos limites, registrada com confirmação')).toBeVisible();
  await expect(ata.getByText('250 de 200')).toBeVisible();

  // histórico da licitação registra ata, carona e documento (é permanente: rodadas anteriores também aparecem)
  await page.getByRole('tab', { name: 'Histórico' }).click();
  await expect(page.getByText(/Carona de Casa Civil .* confirmada/).first()).toBeVisible();
  await expect(page.getByText(/Ata ARP 022\/2026 registrada/).first()).toBeVisible();

  // limpeza: caronas, ata e documento (apagar documento é lógico)
  await page.getByRole('tab', { name: 'Documentos' }).click();
  for (const agency of ['Casa Civil', 'SEGOV · Secretaria de Governo']) {
    await ata.getByRole('button', { name: `Apagar a carona de ${agency}` }).click();
    await ata.getByRole('button', { name: 'Apagar', exact: true }).click();
    await expect(ata.getByText(agency, { exact: true })).toHaveCount(0);
  }
  await ata.getByRole('button', { name: 'Editar' }).click();
  const edit = page.getByRole('dialog', { name: 'Editar ata' });
  await edit.getByRole('button', { name: 'Apagar' }).click();
  await edit.getByRole('button', { name: 'Apagar' }).click();
  await expect(page.getByText('Esta licitação ainda não tem ata.')).toBeVisible();
  await docs.getByRole('button', { name: 'Detalhes' }).click();
  await docs.getByRole('button', { name: 'Apagar', exact: true }).click();
  await docs.getByRole('button', { name: 'Apagar', exact: true }).click();
  await expect(docs.getByText('Nenhum documento.', { exact: false })).toBeVisible();
});

test('atas vigentes como oportunidade, municípios sem ata e destaque nos encerrados', async ({ page }) => {
  await login(page);
  await page.goto('/atas');
  // a ata da SEIJES: contrato encerrado, ata válida até 2028
  const seijes = page.getByRole('article', { name: /ARP 014\/2025/ });
  await expect(seijes).toBeVisible();
  await expect(seijes.getByText('04/11/2028')).toBeVisible();
  await expect(seijes.getByRole('button', { name: 'Oferecer carona' })).toBeVisible();

  // vencendo: a do Comando do Exército
  await page.getByRole('tab', { name: /Vencendo/ }).click();
  await expect(page.getByRole('article', { name: /ARP 008\/2026/ })).toContainText('Vence em');

  // municípios sem ata: prefeituras do RJ sem licitação vencida e sem carona
  await page.getByRole('tab', { name: 'Municípios sem ata' }).click();
  await expect(page).toHaveURL(/aba=MUNICIPIOS/);
  await expect(page.getByRole('row', { name: /Prefeitura Municipal de Nova Iguaçu/ })).toBeVisible();
  await expect(page.getByRole('row', { name: /Prefeitura Municipal de Magé/ })).toHaveCount(0);

  // contratos encerrados mostram a ata ainda vigente
  await page.goto('/licitacoes?aba=encerrados&q=SEIJES');
  await expect(page.getByText('Ata válida até 04/11/2028')).toBeVisible();
});
