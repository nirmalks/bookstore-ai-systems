ALTER TABLE book ADD COLUMN IF NOT EXISTS purchase_count INT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_book_purchase_count ON book (purchase_count DESC);
