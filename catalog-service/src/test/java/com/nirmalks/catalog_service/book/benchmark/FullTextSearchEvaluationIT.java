package com.nirmalks.catalog_service.book.benchmark;

import com.nirmalks.catalog_service.book.entity.Book;
import com.nirmalks.catalog_service.book.repository.BookRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
class FullTextSearchEvaluationIT {

    private static final Logger log = LoggerFactory.getLogger(FullTextSearchEvaluationIT.class);

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @MockitoBean
    private RedissonClient redissonClient;

    @Autowired
    private BookRepository bookRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private final SearchEvaluator evaluator = new SearchEvaluator();
    private final Map<String, Long> keyToIdMap = new HashMap<>();

    @BeforeEach
    void setUp() {
        bookRepository.deleteAll();
        keyToIdMap.clear();

        for (BenchmarkDataset.BenchmarkBook fixture : BenchmarkDataset.BOOKS) {
            Book book = new Book();
            book.setTitle(fixture.title());
            book.setDescription(fixture.description());
            book.setIsbn(fixture.isbn());
            book.setPrice(fixture.price());
            book.setStock(fixture.stockQuantity());
            book.setPurchaseCount(fixture.purchaseCount());
            book.setPublishedDate(java.time.LocalDate.of(2023, 1, 1));
            book.setAuthors(new ArrayList<>());
            book.setGenres(new ArrayList<>());

            Book saved = bookRepository.save(book);
            keyToIdMap.put(fixture.key(), saved.getId());
        }
    }

    @Test
    @DisplayName("AC-1: Understand and demonstrate PostgreSQL FTS linguistic mechanics (stemming and stop words)")
    void shouldDemonstrateFtsLinguisticMechanics() {
        // 1. Inspect lexeme normalization and stemming
        String docVectorSql = "SELECT to_tsvector('english', 'running architectures systems')::text";
        String docVector = (String) entityManager.createNativeQuery(docVectorSql).getSingleResult();
        log.info("Document tsvector: {}", docVector);
        // 'architectur':2 'run':1 'system':3
        assertThat(docVector).contains("'architectur':").contains("'run':").contains("'system':");

        String architectingSql = "SELECT to_tsvector('english', 'architecting architecture')::text";
        String architectingVector = (String) entityManager.createNativeQuery(architectingSql).getSingleResult();
        log.info("Stemming inspection ('architecting architecture'): {}", architectingVector);
        // 'architecting' stems to 'architect', whereas 'architecture' stems to 'architectur'
        assertThat(architectingVector).contains("'architect':1").contains("'architectur':2");

        String queryTsquerySql = "SELECT websearch_to_tsquery('english', 'running architectures systems')::text";
        String queryTsquery = (String) entityManager.createNativeQuery(queryTsquerySql).getSingleResult();
        log.info("Query tsquery: {}", queryTsquery);
        // 'run' & 'architectur' & 'system'
        assertThat(queryTsquery).contains("'run'").contains("'architectur'").contains("'system'");

        // 2. Stop-word removal
        String stopWordSql = "SELECT to_tsvector('english', 'The quick brown fox jumps over the lazy dog in the forest')::text";
        String stopWordVector = (String) entityManager.createNativeQuery(stopWordSql).getSingleResult();
        log.info("Stop-word tsvector: {}", stopWordVector);
        // Stop words 'the', 'over', 'in' are filtered out
        assertThat(stopWordVector).doesNotContain("'the'");
        assertThat(stopWordVector).doesNotContain("'over'");
        assertThat(stopWordVector).doesNotContain("'in'");
        assertThat(stopWordVector).contains("'quick'").contains("'brown'").contains("'fox'").contains("'jump'").contains("'dog'");
    }

    @Test
    @DisplayName("AC-2: Demonstrate field weighting (Title match Weight A=1.0 vs Description match Weight B=0.4)")
    void shouldDemonstrateFieldWeightingDifference() {
        // In V7__add_book_full_text_search.sql:
        // Title is setweight('A') -> 1.0
        // Description is setweight('B') -> 0.4
        // Compare ts_rank for title match vs description match with equal text length
        String rankSql = """
                SELECT b.title,
                       ts_rank(b.search_vector, websearch_to_tsquery('english', 'craftsmanship'), 32) AS rank
                FROM book b
                WHERE b.isbn IN ('978-0-01-000011-1', '978-0-01-000012-2')
                ORDER BY rank DESC
                """;

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(rankSql).getResultList();

        assertThat(rows).hasSize(2);
        String title1 = (String) rows.get(0)[0];
        float rank1 = ((Number) rows.get(0)[1]).floatValue();

        String title2 = (String) rows.get(1)[0];
        float rank2 = ((Number) rows.get(1)[1]).floatValue();

        log.info("Title: '{}' -> rank: {}", title1, rank1);
        log.info("Title: '{}' -> rank: {}", title2, rank2);

        // B11 has 'Craftsmanship' in Title (Weight A), B12 has 'craftsmanship' in Description (Weight B)
        assertThat(title1).contains("Clean Code");
        assertThat(rank1).isGreaterThan(rank2);
    }

    @Test
    @DisplayName("AC-4: Compare FTS vs ILIKE on word order and inflections, and inspect query plans")
    void shouldCompareFtsAndIlikeMechanicsAndPlans() {
        // Query: "systems architecting" (inverted order + inflection)
        // 1. ILIKE phrase matching fails due to word order and inflection
        String ilikePhraseSql = """
                SELECT b.id FROM book b
                WHERE b.title ILIKE '%systems architecting%' OR b.description ILIKE '%systems architecting%'
                """;
        @SuppressWarnings("unchecked")
        List<Number> ilikePhraseResults = entityManager.createNativeQuery(ilikePhraseSql).getResultList();
        assertThat(ilikePhraseResults).isEmpty();

        // 2. FTS matches because words are stemmed and evaluated independently
        String ftsSql = """
                SELECT b.id FROM book b, websearch_to_tsquery('english', 'systems architecting') tsq
                WHERE b.search_vector @@ tsq
                """;
        @SuppressWarnings("unchecked")
        List<Number> ftsResults = entityManager.createNativeQuery(ftsSql).getResultList();
        // Should match B04 ("Architecting Distributed Systems"), B05 ("Software Systems Architecture"), B07, B18, etc.
        assertThat(ftsResults).isNotEmpty();

        // 3. Inspect Query Plans with EXPLAIN (ANALYZE, BUFFERS)
        String explainFtsSql = """
                EXPLAIN (ANALYZE, BUFFERS)
                SELECT b.id FROM book b, websearch_to_tsquery('english', 'cloud architecture') tsq
                WHERE b.search_vector @@ tsq
                """;
        @SuppressWarnings("unchecked")
        List<String> ftsPlan = entityManager.createNativeQuery(explainFtsSql).getResultList();
        log.info("=== FTS Query Plan ===");
        ftsPlan.forEach(line -> log.info("  {}", line));
        assertThat(ftsPlan.stream().anyMatch(line -> line.contains("Index Scan") || line.contains("Bitmap") || line.contains("Seq Scan"))).isTrue();

        String explainIlikeSql = """
                EXPLAIN (ANALYZE, BUFFERS)
                SELECT b.id FROM book b
                WHERE b.title ILIKE '%cloud architecture%' OR b.description ILIKE '%cloud architecture%'
                """;
        @SuppressWarnings("unchecked")
        List<String> ilikePlan = entityManager.createNativeQuery(explainIlikeSql).getResultList();
        log.info("=== ILIKE Query Plan ===");
        ilikePlan.forEach(line -> log.info("  {}", line));
        // ILIKE with leading wildcard on unindexed column uses Seq Scan
        assertThat(ilikePlan.stream().anyMatch(line -> line.contains("Seq Scan"))).isTrue();
    }

    @Test
    @DisplayName("AC-3 & AC-5: Execute benchmark suite comparing Baseline FTS vs FTS + Popularity vs ILIKE")
    void shouldEvaluateBenchmarkSuiteAcrossAllConfigurations() {
        int kPrecisionRecall = 5;
        int kMrr = 10;

        List<List<Long>> baselineFtsRanked = new ArrayList<>();
        List<List<Long>> popularityFtsRanked = new ArrayList<>();
        List<List<Long>> ilikeRanked = new ArrayList<>();
        List<Set<Long>> groundTruthSuite = new ArrayList<>();

        for (BenchmarkDataset.BenchmarkQuery query : BenchmarkDataset.QUERIES) {
            Set<Long> relevantIds = new HashSet<>();
            for (String key : query.relevantBookKeys()) {
                Long id = keyToIdMap.get(key);
                if (id != null) {
                    relevantIds.add(id);
                }
            }
            groundTruthSuite.add(relevantIds);

            // 1. Baseline FTS (pure lexical ts_rank without popularity)
            List<Long> baselineIds = executeBaselineFts(query.queryText(), 10);
            baselineFtsRanked.add(baselineIds);

            // 2. FTS + Popularity (0.7 * ts_rank + 0.3 * normalized popularity)
            Page<Book> popResult = bookRepository.searchBooksFullText(query.queryText(), null, null, null, null, null,
                    PageRequest.of(0, 10));
            List<Long> popIds = popResult.getContent().stream().map(Book::getId).toList();
            popularityFtsRanked.add(popIds);

            // 3. ILIKE baseline (all terms must match title or description)
            List<Long> ilikeIds = executeIlike(query.queryText(), 10);
            ilikeRanked.add(ilikeIds);
        }

        // Evaluate metrics
        SearchEvaluator.EvaluationResult baselineResult = evaluator.evaluateSuite(baselineFtsRanked, groundTruthSuite, kPrecisionRecall);
        SearchEvaluator.EvaluationResult popularityResult = evaluator.evaluateSuite(popularityFtsRanked, groundTruthSuite, kPrecisionRecall);
        SearchEvaluator.EvaluationResult ilikeResult = evaluator.evaluateSuite(ilikeRanked, groundTruthSuite, kPrecisionRecall);

        // MRR@10
        SearchEvaluator.EvaluationResult baselineMrr = evaluator.evaluateSuite(baselineFtsRanked, groundTruthSuite, kMrr);
        SearchEvaluator.EvaluationResult popularityMrr = evaluator.evaluateSuite(popularityFtsRanked, groundTruthSuite, kMrr);
        SearchEvaluator.EvaluationResult ilikeMrr = evaluator.evaluateSuite(ilikeRanked, groundTruthSuite, kMrr);

        for (int i = 0; i < BenchmarkDataset.QUERIES.size(); i++) {
            var q = BenchmarkDataset.QUERIES.get(i);
            var bRes = evaluator.evaluate(baselineFtsRanked.get(i), groundTruthSuite.get(i), 5);
            var iRes = evaluator.evaluate(ilikeRanked.get(i), groundTruthSuite.get(i), 5);
            log.info("Query {}: '{}' | FTS R@5: {}, ILIKE R@5: {} | FTS hits: {}, ILIKE hits: {}",
                    q.id(), q.queryText(), bRes.recallAtK(), iRes.recallAtK(),
                    baselineFtsRanked.get(i).size(), ilikeRanked.get(i).size());
        }

        log.info("==========================================================================");
        log.info("BENCHMARK EVALUATION SUMMARY (10 Queries across 18 Books):");
        log.info("Baseline FTS:       P@5 = {}, R@5 = {}, MRR@10 = {}",
                String.format("%.4f", baselineResult.precisionAtK()),
                String.format("%.4f", baselineResult.recallAtK()),
                String.format("%.4f", baselineMrr.mrr()));
        log.info("FTS + Popularity:   P@5 = {}, R@5 = {}, MRR@10 = {}",
                String.format("%.4f", popularityResult.precisionAtK()),
                String.format("%.4f", popularityResult.recallAtK()),
                String.format("%.4f", popularityMrr.mrr()));
        log.info("ILIKE Baseline:     P@5 = {}, R@5 = {}, MRR@10 = {}",
                String.format("%.4f", ilikeResult.precisionAtK()),
                String.format("%.4f", ilikeResult.recallAtK()),
                String.format("%.4f", ilikeMrr.mrr()));
        log.info("==========================================================================");

        // FTS matches or outperforms ILIKE in Recall and MRR
        assertThat(baselineMrr.mrr()).isGreaterThanOrEqualTo(ilikeMrr.mrr());
        assertThat(baselineResult.recallAtK()).isGreaterThanOrEqualTo(ilikeResult.recallAtK());

        // Popularity ranking maintains high MRR while blending sales signal
        assertThat(popularityMrr.mrr()).isGreaterThan(0.70);
    }

    @Test
    @DisplayName("AC-6: Demonstrate semantic retrieval boundary / vocabulary mismatch where lexical search fails")
    void shouldDemonstrateSemanticFailureBoundaryOnSynonymAndThematicQueries() {
        // Query Q4: "finding life purpose and personal destiny"
        // Target: B08 ("The Alchemist" - "A philosophical novel about following dreams and discovering one's path.")
        Page<Book> q4Result = bookRepository.searchBooksFullText("finding life purpose and personal destiny",
                null, null, null, null, null, PageRequest.of(0, 10));

        // Zero matches found lexically because no keywords overlap with title or description
        assertThat(q4Result.getContent()).isEmpty();

        Long alchemistId = keyToIdMap.get("B08");
        SearchEvaluator.EvaluationResult q4Eval = evaluator.evaluate(
                q4Result.getContent().stream().map(Book::getId).toList(),
                Set.of(alchemistId),
                5
        );
        assertThat(q4Eval.precisionAtK()).isEqualTo(0.0);
        assertThat(q4Eval.recallAtK()).isEqualTo(0.0);
        assertThat(q4Eval.mrr()).isEqualTo(0.0);

        // Query Q5: "witchcraft wizard spells apprentice"
        // Target: B09 ("Harry Potter and the Sorcerer's Stone" - "The first book introducing the magical world of Hogwarts.")
        Page<Book> q5Result = bookRepository.searchBooksFullText("witchcraft wizard spells apprentice",
                null, null, null, null, null, PageRequest.of(0, 10));

        // Zero matches found lexically
        assertThat(q5Result.getContent()).isEmpty();

        Long harryPotterId = keyToIdMap.get("B09");
        SearchEvaluator.EvaluationResult q5Eval = evaluator.evaluate(
                q5Result.getContent().stream().map(Book::getId).toList(),
                Set.of(harryPotterId),
                5
        );
        assertThat(q5Eval.precisionAtK()).isEqualTo(0.0);
        assertThat(q5Eval.recallAtK()).isEqualTo(0.0);
        assertThat(q5Eval.mrr()).isEqualTo(0.0);

        log.info("Q4 (The Alchemist) FTS result: 0 hits (Vocabulary Mismatch). Motivating pgvector/semantic search.");
        log.info("Q5 (Harry Potter) FTS result: 0 hits (Contextual Mismatch). Motivating pgvector/semantic search.");
    }

    private List<Long> executeBaselineFts(String queryText, int limit) {
        String sql = """
                SELECT b.id
                FROM book b, websearch_to_tsquery('english', :query) tsq
                WHERE b.search_vector @@ tsq
                ORDER BY ts_rank(b.search_vector, tsq, 32) DESC
                LIMIT :limit
                """;
        @SuppressWarnings("unchecked")
        List<Number> ids = entityManager.createNativeQuery(sql)
                .setParameter("query", queryText)
                .setParameter("limit", limit)
                .getResultList();

        return ids.stream().map(Number::longValue).toList();
    }

    private List<Long> executeIlike(String queryText, int limit) {
        String[] terms = queryText.trim().split("\\s+");
        StringBuilder sb = new StringBuilder("SELECT b.id FROM book b WHERE 1=1 ");
        Map<String, String> params = new HashMap<>();
        for (int i = 0; i < terms.length; i++) {
            String paramName = "term" + i;
            sb.append(" AND (LOWER(b.title) LIKE :").append(paramName)
              .append(" OR LOWER(b.description) LIKE :").append(paramName).append(")");
            params.put(paramName, "%" + terms[i].toLowerCase() + "%");
        }
        sb.append(" LIMIT :limit");

        var nativeQuery = entityManager.createNativeQuery(sb.toString());
        params.forEach(nativeQuery::setParameter);
        nativeQuery.setParameter("limit", limit);

        @SuppressWarnings("unchecked")
        List<Number> ids = nativeQuery.getResultList();
        return ids.stream().map(Number::longValue).toList();
    }
}
