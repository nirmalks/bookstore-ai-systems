package com.nirmalks.catalog_service.book.benchmark;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SearchEvaluatorTest {

    private final SearchEvaluator evaluator = new SearchEvaluator();

    @Test
    @DisplayName("Evaluate standard case: Top 5 with 4 relevant results out of 10 in corpus")
    void shouldCalculatePrecisionAndRecallCorrectly() {
        // Spec example:
        // Top 5 results: IDs 1, 2, 3, 4, 99
        // Relevant: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 (10 relevant books in corpus)
        // Precision@5 = 4 / 5 = 0.80
        // Recall@5 = 4 / 10 = 0.40
        // First relevant at rank 1 => RR = 1.0
        List<Long> ranked = List.of(1L, 2L, 3L, 4L, 99L);
        Set<Long> relevant = Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L);

        SearchEvaluator.EvaluationResult result = evaluator.evaluate(ranked, relevant, 5);

        assertThat(result.precisionAtK()).isCloseTo(0.80, within(0.001));
        assertThat(result.recallAtK()).isCloseTo(0.40, within(0.001));
        assertThat(result.mrr()).isCloseTo(1.00, within(0.001));
    }

    @Test
    @DisplayName("Evaluate Reciprocal Rank at different ranks and not in top K")
    void shouldCalculateReciprocalRankAccurately() {
        Set<Long> relevant = Set.of(100L);

        // Rank 1: RR = 1/1 = 1.00
        assertThat(evaluator.evaluate(List.of(100L, 2L, 3L), relevant, 10).mrr())
                .isCloseTo(1.00, within(0.001));

        // Rank 2: RR = 1/2 = 0.50
        assertThat(evaluator.evaluate(List.of(1L, 100L, 3L), relevant, 10).mrr())
                .isCloseTo(0.50, within(0.001));

        // Rank 4: RR = 1/4 = 0.25
        assertThat(evaluator.evaluate(List.of(1L, 2L, 3L, 100L, 5L), relevant, 10).mrr())
                .isCloseTo(0.25, within(0.001));

        // Not in top 10: RR = 0.00
        assertThat(evaluator.evaluate(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 100L), relevant, 10).mrr())
                .isCloseTo(0.00, within(0.001));
    }

    @Test
    @DisplayName("Evaluate MRR across queries as specified in benchmark doc")
    void shouldCalculateMeanReciprocalRankAcrossQueries() {
        // Spec Example 3.3:
        // Q1: rank 1 => RR = 1.00
        // Q2: rank 2 => RR = 0.50
        // Q3: rank 4 => RR = 0.25
        // Q4: Not in top 10 => RR = 0.00
        // MRR@10 = (1 + 0.5 + 0.25 + 0) / 4 = 0.4375
        List<List<Long>> rankedSuite = List.of(
                List.of(101L, 2L, 3L),
                List.of(1L, 102L, 3L),
                List.of(1L, 2L, 3L, 103L, 5L),
                List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L)
        );
        List<Set<Long>> relevantSuite = List.of(
                Set.of(101L),
                Set.of(102L),
                Set.of(103L),
                Set.of(104L) // 104 is not in Q4 top 10
        );

        SearchEvaluator.EvaluationResult aggregate = evaluator.evaluateSuite(rankedSuite, relevantSuite, 10);

        assertThat(aggregate.mrr()).isCloseTo(0.4375, within(0.0001));
    }

    @Test
    @DisplayName("Evaluate edge cases: empty lists, fewer than K results, empty relevant set, k <= 0")
    void shouldHandleEdgeCasesGracefully() {
        // Empty ranked results
        SearchEvaluator.EvaluationResult emptyRanked = evaluator.evaluate(Collections.emptyList(), Set.of(1L), 5);
        assertThat(emptyRanked.precisionAtK()).isEqualTo(0.0);
        assertThat(emptyRanked.recallAtK()).isEqualTo(0.0);
        assertThat(emptyRanked.mrr()).isEqualTo(0.0);

        // Fewer results than K (e.g. 2 results returned, 1 relevant, K=5)
        // Precision@5 = 1 / 5 = 0.2
        SearchEvaluator.EvaluationResult fewerThanK = evaluator.evaluate(List.of(1L, 2L), Set.of(1L), 5);
        assertThat(fewerThanK.precisionAtK()).isCloseTo(0.20, within(0.001));
        assertThat(fewerThanK.recallAtK()).isCloseTo(1.00, within(0.001));
        assertThat(fewerThanK.mrr()).isCloseTo(1.00, within(0.001));

        // Empty relevant set
        SearchEvaluator.EvaluationResult emptyRelevant = evaluator.evaluate(List.of(1L, 2L), Collections.emptySet(), 5);
        assertThat(emptyRelevant.precisionAtK()).isEqualTo(0.0);
        assertThat(emptyRelevant.recallAtK()).isEqualTo(0.0);
        assertThat(emptyRelevant.mrr()).isEqualTo(0.0);

        // K <= 0
        SearchEvaluator.EvaluationResult nonPositiveK = evaluator.evaluate(List.of(1L), Set.of(1L), 0);
        assertThat(nonPositiveK.precisionAtK()).isEqualTo(0.0);
        assertThat(nonPositiveK.recallAtK()).isEqualTo(0.0);
        assertThat(nonPositiveK.mrr()).isEqualTo(0.0);

        // Empty suite
        SearchEvaluator.EvaluationResult emptySuite = evaluator.evaluateSuite(Collections.emptyList(), Collections.emptyList(), 10);
        assertThat(emptySuite.precisionAtK()).isEqualTo(0.0);
        assertThat(emptySuite.recallAtK()).isEqualTo(0.0);
        assertThat(emptySuite.mrr()).isEqualTo(0.0);
    }
}
