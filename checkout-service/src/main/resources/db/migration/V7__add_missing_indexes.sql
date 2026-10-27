-- Indexes for foreign key columns to optimize order history, order items retrieval, and cart retrieval
CREATE INDEX idx_purchase_order_user_id ON purchase_order(user_id);
CREATE INDEX idx_order_item_order_id ON Order_Item(order_id);
CREATE INDEX idx_cart_item_cart_id ON Cart_Item(cart_id);

-- Optimize outbox queries: replace the single-column status index with a partial index sorted by created_at for PENDING items
DROP INDEX IF EXISTS idx_outbox_status;
CREATE INDEX idx_outbox_pending_created_at ON outbox(created_at) WHERE status = 'PENDING';
