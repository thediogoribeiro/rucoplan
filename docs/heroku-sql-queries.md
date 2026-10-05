# Heroku SQL Queries

Useful PostgreSQL queries for operating RucoPlan on Heroku. Most queries are read-only; sections marked as maintenance write data and should be used carefully.

Run them with:

```bash
heroku pg:psql
```

For safer manual inspection sessions:

```sql
begin read only;
-- run inspection queries
rollback;
```

Never paste passwords, Telegram tokens or webhook secrets into SQL output, tickets or logs.

## Index

- [Create A New Admin User](#create-a-new-admin-user)
- [Reset A User Password](#reset-a-user-password)
- [Authentication Users](#authentication-users)
- [Production Sites](#production-sites)
- [Data Isolation PT/LUX](#data-isolation-ptlux)
- [Demo Data Checks](#demo-data-checks)
- [Requests And Planning](#requests-and-planning)
- [Targets And Daily Settings](#targets-and-daily-settings)
- [Drivers And Messaging](#drivers-and-messaging)
- [Alerts And Audit](#alerts-and-audit)
- [Heroku Health Sanity Checks](#heroku-health-sanity-checks)

## Create A New Admin User

Maintenance query. Generate a BCrypt hash first with `scripts/generate-bcrypt-password.sh`, then use the generated hash below. Do not paste the plaintext password into SQL.

This example creates an active `ADMIN` user and grants access to both `PT` and `LUX`.

```sql
begin;

with new_user as (
    insert into app_user (
        id,
        username,
        display_name,
        role,
        password_hash,
        driver_id,
        active,
        created_at,
        updated_at,
        created_by,
        updated_by,
        version
    )
    values (
        gen_random_uuid(),
        'replace-with-username',
        'replace-with-display-name',
        'ADMIN',
        'replace-with-generated-bcrypt-hash',
        null,
        true,
        now(),
        now(),
        'MANUAL_ADMIN_CREATE',
        'MANUAL_ADMIN_CREATE',
        0
    )
    returning id, username, display_name, role, active
),
site_access as (
    insert into application_user_site (
        id,
        user_id,
        production_site_id,
        role,
        active,
        created_at,
        created_by
    )
    select
        gen_random_uuid(),
        new_user.id,
        production_site.id,
        new_user.role,
        true,
        now(),
        'MANUAL_ADMIN_CREATE'
    from new_user
    join production_site on production_site.code in ('PT', 'LUX')
    returning user_id
)
select
    new_user.username,
    new_user.display_name,
    new_user.role,
    new_user.active,
    count(site_access.user_id) as granted_sites
from new_user
left join site_access on site_access.user_id = new_user.id
group by new_user.username, new_user.display_name, new_user.role, new_user.active;

commit;
```

If the returned `granted_sites` is not `2`, run `rollback;` instead of `commit;` and inspect `production_site`.

## Reset A User Password

Passwords cannot be recovered from `app_user`. The database stores BCrypt hashes only.

Generate a new BCrypt hash locally, then update the target user in Heroku PostgreSQL. Do not paste the plaintext password into SQL, logs or tickets.

```sql
begin;

select username, display_name, role, active
from app_user
where username = 'replace-with-username';

update app_user
set password_hash = 'replace-with-generated-bcrypt-hash',
    updated_at = now(),
    updated_by = 'PASSWORD_RESET'
where username = 'replace-with-username'
returning username, display_name, role, active, updated_at, updated_by;

commit;
```

If the returned row is not exactly the intended user, run `rollback;` instead of `commit;`.

Alternative: reset directly inside `heroku pg:psql` without copying a hash between terminals. The `\prompt` line is required because `:'new_password'` is a `psql` variable, not normal SQL.

Run step 1 by itself first. Do not paste the whole block on the same prompt line, and do not replace `new_password` with the actual password.

```sql
\prompt 'Nova password: ' new_password
```

Then type the new password when prompted. After that, paste step 2:

```sql

begin;

update app_user
set password_hash = crypt(:'new_password', gen_salt('bf', 10)),
    updated_at = now(),
    updated_by = 'PASSWORD_RESET'
where username = 'replace-with-username'
returning username, display_name, role, active, updated_at, updated_by;

select
    username,
    password_hash = crypt(:'new_password', password_hash) as password_matches
from app_user
where username = 'replace-with-username';

commit;

\unset new_password
```

`password_matches` must return `t`. If it returns `f`, run the reset again and make sure the password was typed as intended.

## Authentication Users

List production users:

```sql
select id, username, display_name, role, active, created_at, updated_at
from app_user
order by created_at, username;
```

Passwords are not stored in plaintext. Production stores password hashes only; do not print full hashes into logs, tickets or chat.

Check users and password hash status without exposing the hash:

```sql
select
    username,
    display_name,
    role,
    active,
    case
        when password_hash is null or password_hash = '' then 'MISSING'
        when password_hash like '$2%' then 'BCRYPT'
        else 'UNKNOWN_HASH_FORMAT'
    end as password_storage,
    length(password_hash) as password_hash_length,
    created_at,
    updated_at
from app_user
order by username;
```

Check user access per production site:

```sql
select
    u.username,
    u.display_name,
    u.role as global_role,
    s.code as site,
    aus.role as site_role,
    aus.active as site_access_active,
    aus.created_at
from app_user u
left join application_user_site aus on aus.user_id = u.id
left join production_site s on s.id = aus.production_site_id
order by u.username, s.code;
```

Find active admins:

```sql
select u.username, u.display_name, string_agg(s.code::text, ', ' order by s.code) as sites
from app_user u
left join application_user_site aus on aus.user_id = u.id and aus.active = true
left join production_site s on s.id = aus.production_site_id
where u.role = 'ADMIN'
  and u.active = true
group by u.id, u.username, u.display_name
order by u.username;
```

Check if bootstrap should still be disabled after first deploy:

```sql
select count(*) as app_user_count
from app_user;
```

## Production Sites

List production sites:

```sql
select id, code, display_name, timezone, active, created_at, updated_at
from production_site
order by code;
```

Check all active site/user associations:

```sql
select s.code, count(aus.id) as active_user_accesses
from production_site s
left join application_user_site aus on aus.production_site_id = s.id and aus.active = true
group by s.code
order by s.code;
```

## Data Isolation PT/LUX

Count main functional data per site:

```sql
select 'customers' as table_name, s.code, count(*) as rows
from customer_reference c
join production_site s on s.id = c.production_site_id
group by s.code
union all
select 'requests', s.code, count(*)
from wheel_intake_request r
join production_site s on s.id = r.production_site_id
group by s.code
union all
select 'plans', s.code, count(*)
from production_plan p
join production_site s on s.id = p.production_site_id
group by s.code
union all
select 'plan_items', s.code, count(*)
from production_plan_item i
join production_site s on s.id = i.production_site_id
group by s.code
union all
select 'targets', s.code, count(*)
from production_target_configuration t
join production_site s on s.id = t.production_site_id
group by s.code
order by table_name, code;
```

Check request wheel totals by site:

```sql
select
    s.code,
    count(*) as requests,
    coalesce(sum(r.expected_wheel_quantity), 0) as expected_wheels,
    coalesce(sum(r.completed_wheel_quantity), 0) as completed_wheels,
    coalesce(sum(greatest(r.expected_wheel_quantity - r.completed_wheel_quantity, 0)), 0) as remaining_wheels
from wheel_intake_request r
join production_site s on s.id = r.production_site_id
group by s.code
order by s.code;
```

Check wheel quantities by wheel type and site:

```sql
select
    s.code,
    q.wheel_type,
    coalesce(sum(q.quantity), 0) as quantity
from request_wheel_quantity q
join wheel_intake_request r on r.id = q.request_id
join production_site s on s.id = r.production_site_id
group by s.code, q.wheel_type
order by s.code, q.wheel_type;
```

Find rows that should never have a missing site:

```sql
select 'customer_reference' as table_name, count(*) as missing_site from customer_reference where production_site_id is null
union all
select 'wheel_intake_request', count(*) from wheel_intake_request where production_site_id is null
union all
select 'production_plan', count(*) from production_plan where production_site_id is null
union all
select 'production_plan_item', count(*) from production_plan_item where production_site_id is null
union all
select 'production_target_configuration', count(*) from production_target_configuration where production_site_id is null
union all
select 'daily_production_settings', count(*) from daily_production_settings where production_site_id is null
union all
select 'capacity_alert', count(*) from capacity_alert where production_site_id is null
union all
select 'planning_run', count(*) from planning_run where production_site_id is null;
```

Detect requests linked to customers from a different site:

```sql
select
    r.id,
    r.request_code,
    rs.code as request_site,
    c.name as customer_name,
    cs.code as customer_site
from wheel_intake_request r
join customer_reference c on c.id = r.customer_reference_id
join production_site rs on rs.id = r.production_site_id
join production_site cs on cs.id = c.production_site_id
where r.production_site_id <> c.production_site_id
order by r.created_at desc;
```

Detect plan items whose plan/request site does not match the item site:

```sql
select
    i.id as plan_item_id,
    ips.code as item_site,
    ps.code as plan_site,
    rs.code as request_site,
    p.planning_date,
    r.request_code
from production_plan_item i
join production_site ips on ips.id = i.production_site_id
join production_plan p on p.id = i.plan_id
join production_site ps on ps.id = p.production_site_id
join wheel_intake_request r on r.id = i.request_id
join production_site rs on rs.id = r.production_site_id
where i.production_site_id <> p.production_site_id
   or i.production_site_id <> r.production_site_id
order by p.planning_date desc, r.request_code;
```

## Demo Data Checks

Production should not contain local demo customers:

```sql
select s.code, c.customer_code, c.name, c.created_at, c.created_by
from customer_reference c
join production_site s on s.id = c.production_site_id
where c.normalized_name like 'loja de jantes de %'
   or c.created_by = 'DEV_SEED'
order by s.code, c.name;
```

Production should not contain local demo requests:

```sql
select s.code, r.request_code, r.customer_name_snapshot, r.expected_wheel_quantity, r.created_at, r.created_by
from wheel_intake_request r
join production_site s on s.id = r.production_site_id
where r.request_code like 'REQ-DEV%'
   or r.request_code like 'REQ-LUX%'
   or r.created_by = 'DEV_SEED'
order by r.created_at desc;
```

Production should not contain local demo driver data:

```sql
select d.id, d.driver_code, d.name, d.active, d.created_at, d.created_by
from driver d
where lower(d.name) = 'teste'
   or d.created_by = 'DEV_SEED'
order by d.created_at desc;
```

## Requests And Planning

Recent requests:

```sql
select
    s.code as site,
    r.request_code,
    r.lifecycle_status,
    r.source,
    r.customer_name_snapshot,
    r.expected_wheel_quantity,
    r.completed_wheel_quantity,
    greatest(r.expected_wheel_quantity - r.completed_wheel_quantity, 0) as remaining_wheel_quantity,
    r.expected_factory_dropoff_end,
    r.requested_factory_pickup_start,
    r.created_at
from wheel_intake_request r
join production_site s on s.id = r.production_site_id
order by r.created_at desc
limit 50;
```

Current plans by site and date:

```sql
select
    s.code as site,
    p.planning_date,
    p.version_number,
    p.current_plan,
    p.status,
    p.total_planned,
    p.total_completed,
    p.total_remaining,
    p.generated_at,
    p.generation_trigger
from production_plan p
join production_site s on s.id = p.production_site_id
where p.current_plan = true
order by p.planning_date desc, s.code
limit 60;
```

Plan lines for one site/date:

```sql
select
    s.code as site,
    p.planning_date,
    r.request_code,
    i.customer_name,
    i.driver_name,
    i.quantity as planned_quantity,
    i.completed_quantity,
    i.remaining_quantity,
    i.line_status,
    i.risk_classification,
    i.priority_score
from production_plan_item i
join production_plan p on p.id = i.plan_id
join wheel_intake_request r on r.id = i.request_id
join production_site s on s.id = i.production_site_id
where s.code = 'PT'
  and p.planning_date = current_date
  and p.current_plan = true
order by i.priority_score asc;
```

Latest planning runs:

```sql
select
    s.code as site,
    r.trigger,
    r.status,
    r.started_at,
    r.finished_at,
    r.affected_date_from,
    r.affected_date_to,
    r.actor,
    r.summary
from planning_run r
join production_site s on s.id = r.production_site_id
order by r.started_at desc
limit 50;
```

## Targets And Daily Settings

Current target configurations:

```sql
select
    s.code as site,
    t.effective_from,
    t.minimum_daily_target,
    t.regular_daily_capacity,
    t.created_at,
    t.created_by
from production_target_configuration t
join production_site s on s.id = t.production_site_id
order by s.code, t.effective_from desc;
```

Daily settings by site:

```sql
select
    s.code as site,
    d.settings_date,
    d.settings_key,
    d.daily_target,
    d.daily_capacity,
    d.fallback_minutes_per_wheel,
    d.created_at,
    d.updated_at
from daily_production_settings d
join production_site s on s.id = d.production_site_id
order by d.settings_date desc, s.code
limit 60;
```

## Drivers And Messaging

Drivers and allowed production sites:

```sql
select
    d.driver_code,
    d.name,
    d.active,
    string_agg(s.code::text, ', ' order by s.code) as allowed_sites,
    d.telegram_user_id is not null as has_legacy_telegram_identity,
    d.created_at,
    d.updated_at
from driver d
left join driver_production_site dps on dps.driver_id = d.id and dps.active = true
left join production_site s on s.id = dps.production_site_id
group by d.id, d.driver_code, d.name, d.active, d.telegram_user_id, d.created_at, d.updated_at
order by d.name;
```

Messaging identities by channel:

```sql
select
    mi.channel,
    mi.integration_key,
    mi.external_username,
    mi.onboarding_status,
    d.driver_code,
    d.name as driver_name,
    mi.last_seen_at,
    mi.created_at
from messaging_identity mi
left join driver d on d.id = mi.driver_id
order by mi.last_seen_at desc nulls last, mi.created_at desc
limit 50;
```

Telegram inbound update processing status:

```sql
select status, count(*) as updates, max(received_at) as latest_received_at
from telegram_inbound_update
group by status
order by status;
```

Repeated Telegram updates should have one row per `update_id`:

```sql
select update_id, count(*) as rows
from telegram_inbound_update
group by update_id
having count(*) > 1
order by rows desc, update_id desc;
```

Active Telegram drafts by site:

```sql
select
    s.code as site,
    d.status,
    count(*) as drafts,
    max(d.updated_at) as latest_updated_at
from telegram_intake_draft d
join production_site s on s.id = d.production_site_id
group by s.code, d.status
order by s.code, d.status;
```

## Alerts And Audit

Open capacity alerts:

```sql
select
    s.code as site,
    a.type,
    a.status,
    a.affected_date,
    a.required_quantity,
    a.available_capacity,
    a.deficit,
    a.created_at,
    a.resolved_at
from capacity_alert a
join production_site s on s.id = a.production_site_id
where a.status in ('ACTIVE', 'ACKNOWLEDGED')
order by a.affected_date asc, s.code;
```

Recent planning audit events:

```sql
select
    s.code as site,
    a.event_type,
    a.actor,
    a.detail,
    a.created_at
from planning_audit_event a
left join production_site s on s.id = a.production_site_id
order by a.created_at desc
limit 50;
```

## Heroku Health Sanity Checks

Database size:

```sql
select pg_size_pretty(pg_database_size(current_database())) as database_size;
```

Largest application tables:

```sql
select
    schemaname,
    relname as table_name,
    n_live_tup as estimated_rows,
    pg_size_pretty(pg_total_relation_size(relid)) as total_size
from pg_stat_user_tables
order by pg_total_relation_size(relid) desc
limit 20;
```

Currently active database connections:

```sql
select state, count(*) as connections
from pg_stat_activity
where datname = current_database()
group by state
order by state;
```
