import { describe, expect, it } from 'vitest';
import { formatBrlInput, parseBrl } from './format';

describe('parseBrl', () => {
  it('entende o jeito brasileiro de escrever valores', () => {
    expect(parseBrl('174.000,00')).toBe(174000);
    expect(parseBrl('R$ 1.234,5')).toBe(1234.5);
    expect(parseBrl('70900,26')).toBe(70900.26);
    expect(parseBrl('1234.56')).toBe(1234.56);
    expect(parseBrl('300000')).toBe(300000);
  });

  it('recusa o que não é valor', () => {
    expect(parseBrl('')).toBeNull();
    expect(parseBrl('abc')).toBeNull();
    expect(parseBrl('1,234')).toBeNull();
    expect(parseBrl(null)).toBeNull();
  });

  it('formata para o campo', () => {
    expect(formatBrlInput(174000)).toBe('174.000,00');
    expect(formatBrlInput(null)).toBe('');
  });
});
