import { whatsappNumber } from './follow-up-writer';

describe('whatsappNumber', () => {
  it('põe o 55 no número brasileiro com DDD', () => {
    expect(whatsappNumber('(21) 99999-0000')).toBe('5521999990000');
    expect(whatsappNumber('21 3333-4444')).toBe('552133334444');
  });

  it('mantém o número que já tem o código do país', () => {
    expect(whatsappNumber('+55 21 99999-0000')).toBe('5521999990000');
  });

  it('ignora número curto ou vazio', () => {
    expect(whatsappNumber('9999-0000')).toBeNull();
    expect(whatsappNumber(null)).toBeNull();
  });
});
