package com.nirmalks.catalog_service.book.repository;

import com.nirmalks.catalog_service.book.entity.Book;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

	@Query("SELECT b FROM Book b JOIN b.genres g WHERE g.name = :genreName")
	Page<Book> findAllByGenresName(@Param("genreName") String genreName, Pageable pageable);

	@Query("SELECT b FROM Book b JOIN b.authors a WHERE a.id = :authorId")
	Page<Book> findAllByAuthorsId(@Param("authorId") Long authorId, Pageable pageable);

	Page<Book> findAll(@Nullable Specification<Book> specification, @NonNull Pageable pageable);

	@Modifying
	@Query("UPDATE Book b SET b.stock = b.stock - :quantity WHERE b.id = :id AND b.stock >= :quantity")
	int decrementStock(Long id, int quantity);

	@Modifying
	@Query("UPDATE Book b SET b.stock = b.stock + :quantity WHERE b.id = :bookId")
	int incrementStock(@Param("bookId") Long bookId, @Param("quantity") int quantity);

	@Modifying
	@Query("UPDATE Book b SET b.purchaseCount = b.purchaseCount + :quantity WHERE b.id = :bookId")
	int incrementPurchaseCount(@Param("bookId") Long bookId, @Param("quantity") int quantity);

	@Query(value = """
			WITH search_query AS (
			    SELECT websearch_to_tsquery('english', CAST(:query AS text)) AS tsq
			)
			SELECT b.*,
			       (b.search_vector @@ sq.tsq) AS book_match,
			       CASE
			           WHEN b.search_vector @@ sq.tsq THEN ts_rank(b.search_vector, sq.tsq)
			           ELSE 0
			       END AS rank
			FROM book b
			CROSS JOIN search_query sq
			WHERE (
			    b.search_vector @@ sq.tsq
			    OR EXISTS (
			        SELECT 1
			        FROM book_author ba
			        JOIN author a ON a.id = ba.author_id
			        WHERE ba.book_id = b.id
			          AND to_tsvector('english', a.name) @@ sq.tsq
			    )
			  )
			  AND (
			    CAST(:genre AS text) IS NULL
			    OR EXISTS (
			        SELECT 1
			        FROM book_genre bg
			        JOIN genre g ON g.id = bg.genre_id
			        WHERE bg.book_id = b.id
			          AND LOWER(g.name) = LOWER(CAST(:genre AS text))
			    )
			  )
			  AND (CAST(:startDate AS date) IS NULL OR b.published_date >= CAST(:startDate AS date))
			  AND (CAST(:endDate AS date) IS NULL OR b.published_date <= CAST(:endDate AS date))
			  AND (CAST(:minPrice AS numeric) IS NULL OR b.price >= CAST(:minPrice AS numeric))
			  AND (CAST(:maxPrice AS numeric) IS NULL OR b.price <= CAST(:maxPrice AS numeric))
			ORDER BY book_match DESC, rank DESC, b.id ASC
			""", countQuery = """
			WITH search_query AS (
			    SELECT websearch_to_tsquery('english', CAST(:query AS text)) AS tsq
			)
			SELECT count(b.id)
			FROM book b
			CROSS JOIN search_query sq
			WHERE (
			    b.search_vector @@ sq.tsq
			    OR EXISTS (
			        SELECT 1
			        FROM book_author ba
			        JOIN author a ON a.id = ba.author_id
			        WHERE ba.book_id = b.id
			          AND to_tsvector('english', a.name) @@ sq.tsq
			    )
			  )
			  AND (
			    CAST(:genre AS text) IS NULL
			    OR EXISTS (
			        SELECT 1
			        FROM book_genre bg
			        JOIN genre g ON g.id = bg.genre_id
			        WHERE bg.book_id = b.id
			          AND LOWER(g.name) = LOWER(CAST(:genre AS text))
			    )
			  )
			  AND (CAST(:startDate AS date) IS NULL OR b.published_date >= CAST(:startDate AS date))
			  AND (CAST(:endDate AS date) IS NULL OR b.published_date <= CAST(:endDate AS date))
			  AND (CAST(:minPrice AS numeric) IS NULL OR b.price >= CAST(:minPrice AS numeric))
			  AND (CAST(:maxPrice AS numeric) IS NULL OR b.price <= CAST(:maxPrice AS numeric))
			""", nativeQuery = true)
	Page<Book> searchBooksFullText(@Param("query") String query, @Param("genre") String genre,
			@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate,
			@Param("minPrice") Double minPrice, @Param("maxPrice") Double maxPrice, Pageable pageable);

}
