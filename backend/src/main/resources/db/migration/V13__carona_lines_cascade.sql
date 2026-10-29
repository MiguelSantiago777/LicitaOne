-- Apagar uma ata leva itens e caronas juntos (cascata); a linha da carona também tem que sair quando o item sai,
-- senão a ordem da cascata decide se a exclusão passa. A aplicação continua recusando remover item com carona.
alter table carona_lines drop constraint carona_lines_item_id_fkey;
alter table carona_lines
    add constraint carona_lines_item_id_fkey foreign key (item_id) references price_record_items (id) on delete cascade;
