UPDATE production_target_configuration target
SET minimum_daily_target = 13,
    regular_daily_capacity = 30
FROM production_site site
WHERE target.production_site_id = site.id
  AND site.code = 'LUX'
  AND target.created_by = 'SYSTEM'
  AND target.effective_from = DATE '1970-01-01'
  AND target.minimum_daily_target = 20
  AND target.regular_daily_capacity = 60;
