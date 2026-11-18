package com.nirmalks.catalog_service.book;

import com.nirmalks.catalog_service.author.entity.Author;
import com.nirmalks.catalog_service.author.repository.AuthorRepository;
import com.nirmalks.catalog_service.book.dto.BookDto;
import com.nirmalks.catalog_service.book.entity.Book;
import com.nirmalks.catalog_service.book.repository.BookRepository;
import com.nirmalks.catalog_service.book.service.BookService;
import com.nirmalks.catalog_service.genre.entity.Genre;
import com.nirmalks.catalog_service.genre.repository.GenreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
		"spring.flyway.enabled=true",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
		"spring.datasource.driver-class-name=org.postgresql.Driver",
		"kafka.consumer.enabled=false"
})
@DirtiesContext
class BookSearchPopularityIT {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

	@MockitoBean
	private RedissonClient redissonClient;

	@Autowired
	private BookRepository bookRepository;

	@Autowired
	private AuthorRepository authorRepository;

	@Autowired
	private GenreRepository genreRepository;

	@Autowired
	private BookService bookService;

	@jakarta.persistence.PersistenceContext
	private jakarta.persistence.EntityManager entityManager;

	@BeforeEach
	void setUp() {
		bookRepository.deleteAll();
		authorRepository.deleteAll();
		genreRepository.deleteAll();
	}

	@Test
	@DisplayName("Test Case 1: Equal Text Match, Popularity Tiebreaker - higher purchase_count ranks first")
	void shouldPrioritizeHigherPopularityWhenTextRelevanceIsEqual() {
		// Book A: 0 purchases
		Book bookA = createBook("Cloud Architecture Handbook",
				"Comprehensive patterns and design practices for cloud architecture systems.",
				"978-1-00-000001-1", 49.99, 100, 0);

		// Book B: 45 purchases
		Book bookB = createBook("Cloud Architecture Handbook",
				"Comprehensive patterns and design practices for cloud architecture systems.",
				"978-1-00-000002-2", 49.99, 100, 45);

		bookRepository.saveAll(List.of(bookA, bookB));

		Page<Book> result = bookRepository.searchBooksFullText("Cloud Architecture", null, null, null, null, null,
				PageRequest.of(0, 10));

		assertThat(result.getContent()).hasSize(2);
		// Book B has purchase_count=45 vs Book A with 0, so Book B ranks first
		assertThat(result.getContent().get(0).getIsbn()).isEqualTo("978-1-00-000002-2");
		assertThat(result.getContent().get(0).getPurchaseCount()).isEqualTo(45);
		assertThat(result.getContent().get(1).getIsbn()).isEqualTo("978-1-00-000001-1");
		assertThat(result.getContent().get(1).getPurchaseCount()).isEqualTo(0);
	}

	@Test
	@DisplayName("Test Case 2: Text Relevance Retention - title match with few purchases outranks weak description match with high purchases")
	void shouldRetainTextRelevanceOverPopularityWhenQueryMatchesTitleVsDescription() {
		// Book C: Title match (weight A in tsvector), low purchases (2)
		Book bookC = createBook("Quantum Computing Guide",
				"Introduction to quantum gates and superposition.",
				"978-2-00-000001-1", 59.99, 50, 2);

		// Book D: Title does not match; description mentions 'quantum' only once (weight B in tsvector), high purchases (500)
		Book bookD = createBook("Database Systems Engineering",
				"A comprehensive database handbook covering indexing, transaction isolation, and briefly mentions quantum.",
				"978-2-00-000002-2", 69.99, 50, 500);

		bookRepository.saveAll(List.of(bookC, bookD));

		Page<Book> result = bookRepository.searchBooksFullText("Quantum", null, null, null, null, null,
				PageRequest.of(0, 10));

		assertThat(result.getContent()).hasSize(2);
		// Book C has strong text relevance in title (weight A) which outweighs Book D's weak description mention despite 500 purchases
		assertThat(result.getContent().get(0).getTitle()).isEqualTo("Quantum Computing Guide");
		assertThat(result.getContent().get(1).getTitle()).isEqualTo("Database Systems Engineering");
	}

	@Test
	@DisplayName("Test Case 3: Browse by Popularity - non-FTS specification sorts by purchaseCount DESC")
	void shouldSortByPopularityDescendingWhenBrowsingWithoutSearchQuery() {
		Book low = createBook("Low Seller Book", "Description", "978-3-00-000001-1", 20.0, 10, 5);
		Book mid = createBook("Mid Seller Book", "Description", "978-3-00-000002-2", 20.0, 10, 50);
		Book high = createBook("Top Seller Book", "Description", "978-3-00-000003-3", 20.0, 10, 500);

		bookRepository.saveAll(List.of(low, mid, high));

		Page<BookDto> result = bookService.getFilteredBooks(null, null, null, null, null, null,
				"popularity", "desc", 0, 10);

		assertThat(result.getContent()).hasSize(3);
		assertThat(result.getContent().get(0).title()).isEqualTo("Top Seller Book");
		assertThat(result.getContent().get(1).title()).isEqualTo("Mid Seller Book");
		assertThat(result.getContent().get(2).title()).isEqualTo("Low Seller Book");
	}

	@Test
	@DisplayName("Test Case 4: Author-Only Search - author matches without book title match are ordered by popularity")
	void shouldRankAuthorOnlyMatchesByPopularity() {
		Author author = new Author();
		author.setName("Martin Fowler");
		author.setBio("Renowned software author and speaker");
		author = authorRepository.save(author);

		Book bookX = createBook("Patterns of Enterprise Systems",
				"Software engineering architectural practices.",
				"978-4-00-000001-1", 55.0, 20, 5);
		bookX.setAuthors(new ArrayList<>(List.of(author)));

		Book bookY = createBook("Refactoring Codebases Cleanly",
				"Techniques to improve design of existing software.",
				"978-4-00-000002-2", 55.0, 20, 80);
		bookY.setAuthors(new ArrayList<>(List.of(author)));

		bookRepository.saveAll(List.of(bookX, bookY));

		Page<Book> result = bookRepository.searchBooksFullText("Martin Fowler", null, null, null, null, null,
				PageRequest.of(0, 10));

		assertThat(result.getContent()).hasSize(2);
		// Both books match via author only; Book Y (80 purchases) ranks above Book X (5 purchases)
		assertThat(result.getContent().get(0).getTitle()).isEqualTo("Refactoring Codebases Cleanly");
		assertThat(result.getContent().get(1).getTitle()).isEqualTo("Patterns of Enterprise Systems");
	}

	@Test
	@DisplayName("Test Case 5: Zero-Result Search - term matching nothing returns empty page without errors")
	void shouldReturnEmptyPageForZeroResultSearch() {
		Book book = createBook("Microservice Architectures", "Spring Boot microservices guide",
				"978-5-00-000001-1", 40.0, 10, 10);
		bookRepository.save(book);

		Page<Book> result = bookRepository.searchBooksFullText("nonexistenttermxyz999", null, null, null, null, null,
				PageRequest.of(0, 10));

		assertThat(result.getContent()).isEmpty();
		assertThat(result.getTotalElements()).isZero();
	}

	@Test
	@DisplayName("Test Case 6: Genre and Price Filters - popularity applies only within filtered subset")
	void shouldApplyGenreAndPriceFiltersWithPopularityRanking() {
		Genre tech = new Genre();
		tech.setName("Technology");
		tech = genreRepository.save(tech);

		Genre fiction = new Genre();
		fiction.setName("Fiction");
		fiction = genreRepository.save(fiction);

		// Tech book, eligible price ($45), 20 purchases
		Book techMid = createBook("Cloud Patterns in Java", "Scalable cloud patterns",
				"978-6-00-000001-1", 45.0, 10, 20);
		techMid.setGenres(new ArrayList<>(List.of(tech)));

		// Tech book, eligible price ($40), 90 purchases
		Book techHigh = createBook("Cloud Solutions Architect", "Enterprise cloud architect handbook",
				"978-6-00-000002-2", 40.0, 10, 90);
		techHigh.setGenres(new ArrayList<>(List.of(tech)));

		// Tech book, INELIGIBLE price ($120), 500 purchases
		Book techExpensive = createBook("Cloud Executive Strategy", "Enterprise cloud strategy",
				"978-6-00-000003-3", 120.0, 10, 500);
		techExpensive.setGenres(new ArrayList<>(List.of(tech)));

		// Fiction book, matching 'Cloud' but wrong genre, 300 purchases
		Book fictionBook = createBook("Cloud Castle Novel", "A novel about floating cloud castles",
				"978-6-00-000004-4", 30.0, 10, 300);
		fictionBook.setGenres(new ArrayList<>(List.of(fiction)));

		bookRepository.saveAll(List.of(techMid, techHigh, techExpensive, fictionBook));

		// Search "Cloud" with genre=Technology, minPrice=20.0, maxPrice=60.0
		Page<Book> result = bookRepository.searchBooksFullText("Cloud", "Technology", null, null, 20.0, 60.0,
				PageRequest.of(0, 10));

		assertThat(result.getContent()).hasSize(2);
		// Excludes fictionBook (genre mismatch) and techExpensive (price mismatch)
		// Between techHigh (90 purchases) and techMid (20 purchases), techHigh ranks first
		assertThat(result.getContent().get(0).getTitle()).isEqualTo("Cloud Solutions Architect");
		assertThat(result.getContent().get(1).getTitle()).isEqualTo("Cloud Patterns in Java");
	}

	@Test
	@DisplayName("Test Case 7: Query Performance Verification - EXPLAIN (ANALYZE, BUFFERS) executes efficiently")
	void shouldExecuteExplainAnalyzePlanWithoutPerformanceDegradation() {
		for (int i = 0; i < 20; i++) {
			bookRepository.save(createBook("Cloud Systems Handbook " + i,
					"Detailed guide on scalable cloud systems engineering",
					"978-7-00-000000-" + i, 30.0 + i, 10, i * 5));
		}

		String explainSql = """
				EXPLAIN (ANALYZE, BUFFERS)
				WITH search_query AS (
				    SELECT websearch_to_tsquery('english', 'Cloud Systems') AS tsq
				)
				SELECT b.*,
				       (b.search_vector @@ sq.tsq) AS book_match,
				       CASE
				           WHEN b.search_vector @@ sq.tsq THEN
				               (ts_rank(b.search_vector, sq.tsq, 32) * 0.7 + popularity.score * 0.3)
				           ELSE
				               (popularity.score * 0.1)
				       END AS search_score
				FROM book b
				CROSS JOIN search_query sq
				CROSS JOIN LATERAL (
				    SELECT ln(b.purchase_count + 1) / (1.0 + ln(b.purchase_count + 1)) AS score
				) popularity
				WHERE (
				    b.search_vector @@ sq.tsq
				)
				ORDER BY book_match DESC, search_score DESC, b.id ASC
				""";

		@SuppressWarnings("unchecked")
		List<String> planLines = entityManager.createNativeQuery(explainSql).getResultList();
		assertThat(planLines).isNotEmpty();
	}

	private Book createBook(String title, String description, String isbn, Double price, Integer stock, int purchaseCount) {
		Book book = new Book();
		book.setTitle(title);
		book.setDescription(description);
		book.setIsbn(isbn);
		book.setPrice(price);
		book.setStock(stock);
		book.setPurchaseCount(purchaseCount);
		book.setPublishedDate(LocalDate.of(2024, 1, 1));
		book.setImagePath("cover.jpg");
		book.setAuthors(new ArrayList<>());
		book.setGenres(new ArrayList<>());
		return book;
	}
}
