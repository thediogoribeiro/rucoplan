ALTER TABLE customer_reference
    ADD COLUMN IF NOT EXISTS country_name VARCHAR(120);

UPDATE customer_reference
SET country_name = CASE country_code
    WHEN 'PT' THEN 'Portugal'
    WHEN 'LU' THEN 'Luxemburgo'
    WHEN 'FR' THEN 'França'
    WHEN 'ES' THEN 'Espanha'
    ELSE country_code
END
WHERE country_name IS NULL
  AND country_code IS NOT NULL;

COMMENT ON COLUMN customer_reference.country_name IS
    'Nome do país introduzido pelo utilizador. country_code permanece campo técnico opcional para integrações.';

COMMENT ON COLUMN request_status_history.actor_user_id IS
    'Referência ao app_user que representa o ator real ou técnico. A aplicação cria atores técnicos inativos para LOCAL/TELEGRAM/WHATSAPP/SYSTEM quando necessário.';
