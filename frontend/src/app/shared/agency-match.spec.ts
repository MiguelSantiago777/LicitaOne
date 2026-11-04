import { sameAgency } from './agency-match';

describe('sameAgency', () => {
  it('reconhece o mesmo órgão escrito de outro jeito', () => {
    expect(sameAgency('MUNICÍPIO DE MAGÉ - Secretaria de Educação', 'Prefeitura de Magé')).toBe(
      true,
    );
  });

  it('não avisa quando não há palavra significativa para comparar', () => {
    expect(sameAgency('Prefeitura Municipal', 'Prefeitura de Magé')).toBe(true);
  });

  it('aponta órgãos diferentes', () => {
    expect(sameAgency('Prefeitura de Maricá', 'Prefeitura de Magé')).toBe(false);
  });
});
