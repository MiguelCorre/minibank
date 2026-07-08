-- Idempotency keys become scoped per initiating customer: replaying someone
-- else's key must never return (or block on) their transfer.

alter table transfers add column initiated_by uuid;

update transfers t
set initiated_by = a.owner_id
from accounts a
where a.id = t.from_account_id;

alter table transfers alter column initiated_by set not null;

alter table transfers drop constraint transfers_idempotency_key_key;
alter table transfers add constraint uq_transfers_initiator_key unique (initiated_by, idempotency_key);
