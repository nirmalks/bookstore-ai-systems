-- Indexes for many-to-many join tables to optimize reverse lookups (e.g. books by author, books by genre)
CREATE INDEX idx_book_author_author_id ON Book_Author(author_id);
CREATE INDEX idx_book_genre_genre_id ON Book_Genre(genre_id);

-- Drop redundant index on stock_reservation(saga_id) since saga_id has a UNIQUE constraint which automatically creates a unique index
DROP INDEX IF EXISTS idx_stock_reservation_saga_id;
