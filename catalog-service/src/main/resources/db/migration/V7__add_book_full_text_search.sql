-- Flyway Migration: V7__add_book_full_text_search.sql

-- 1. Add generated tsvector column for title and description
ALTER TABLE book 
ADD COLUMN search_vector tsvector 
GENERATED ALWAYS AS (
    setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(description, '')), 'B')
) STORED;

-- 2. Create GIN index for inverted lexeme lookups
CREATE INDEX idx_book_search_vector_gin ON book USING gin(search_vector);

