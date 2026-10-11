-- NOT a backup. Metadata manifest only; encrypted offline pg_dump + restore rehearsal required.
-- Historical optional inventory only. Step 89-3 does NOT require a business-data backup.
BEGIN READ ONLY;
SET LOCAL statement_timeout='30s';
SELECT c.relname,c.reltuples::bigint AS estimated_rows,pg_total_relation_size(c.oid) AS bytes
FROM pg_class c WHERE c.relnamespace='public'::regnamespace AND c.relkind IN ('r','p');
SELECT c.relname,pg_get_serial_sequence(format('public.%I',c.relname),'id') AS id_sequence
FROM pg_class c WHERE c.relnamespace='public'::regnamespace
AND c.relname IN ('company_events','gallery_photos','home_banners','members','notices','test_users');
ROLLBACK;
-- Estimates are not exact counts and do not prove backup completeness.
