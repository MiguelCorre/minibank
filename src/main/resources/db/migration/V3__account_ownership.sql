-- Accounts become owned by a customer. Pre-ownership rows were demo data
-- with no possible owner, so the transactional tables are reset.

delete from ledger_entries;
delete from transfers;
delete from accounts;

alter table accounts
    add column owner_id uuid not null;

alter table accounts
    add constraint fk_accounts_owner foreign key (owner_id) references users (id);

create index idx_accounts_owner on accounts (owner_id);
