-- FX: exchange rates as data, and conversion details on transfers.

create table exchange_rates (
    id             uuid primary key,
    base_currency  varchar(3)     not null,
    quote_currency varchar(3)     not null,
    rate           numeric(19, 6) not null,
    updated_at     timestamp(6) with time zone not null,
    constraint uq_exchange_rates_pair unique (base_currency, quote_currency)
);

insert into exchange_rates (id, base_currency, quote_currency, rate, updated_at)
values (gen_random_uuid(), 'EUR', 'USD', 1.100000, now()),
       (gen_random_uuid(), 'EUR', 'GBP', 0.850000, now()),
       (gen_random_uuid(), 'EUR', 'CHF', 0.950000, now());

-- Existing transfers were all same-currency: converted amount equals the
-- source amount at rate 1.
alter table transfers add column converted_amount numeric(19, 2);
alter table transfers add column target_currency varchar(3);
alter table transfers add column exchange_rate numeric(19, 6);

update transfers
set converted_amount = amount,
    target_currency  = currency,
    exchange_rate    = 1;

alter table transfers alter column converted_amount set not null;
alter table transfers alter column target_currency set not null;
alter table transfers alter column exchange_rate set not null;
