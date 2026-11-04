import { fileName } from './carona-kit-dialog';

describe('fileName (Content-Disposition do .docx do kit de carona)', () => {
  it('prefere o filename* em UTF-8', () => {
    expect(
      fileName(
        `attachment; filename="=?UTF-8?Q?Anu=C3=AAncia.docx?="; filename*=UTF-8''Anu%C3%AAncia%20-%20ARP%20017-2026.docx`,
      ),
    ).toBe('Anuência - ARP 017-2026.docx');
  });

  it('usa o filename simples quando não há o UTF-8', () => {
    expect(fileName('attachment; filename="Anuencia.docx"')).toBe('Anuencia.docx');
  });

  it('sem cabeçalho, devolve nulo', () => {
    expect(fileName(null)).toBeNull();
  });
});
