-- Common platform support: distinguish SCHOOL vs SHOP tenants in one backlog.
ALTER TABLE support_tickets
  ADD COLUMN IF NOT EXISTS product VARCHAR(20) NOT NULL DEFAULT 'SCHOOL';

CREATE INDEX IF NOT EXISTS idx_support_tickets_product_org
  ON support_tickets (product, organization_id, shop_id);
