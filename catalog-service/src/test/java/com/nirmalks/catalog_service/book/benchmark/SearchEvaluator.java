package com.nirmalks.catalog_service.book.benchmark;

import java.util.List;
import java.util.Set;

/**
 * Evaluator for Information Retrieval (IR) metrics:
 * - Precision@K
 * - Recall@K
 * - Reciprocal Rank (RR) / Mean Reciprocal Rank (MRR@K)
 */
public class SearchEvaluator {

    public record EvaluationResult(
            double precisionAtK,
            double recallAtK,
            double mrr
    ) {}

    /**
     * Evaluates a single query's ranked retrieval results against ground truth.
     *
     * @param rankedBookIds ordered list of retrieved book IDs (rank 1 at index 0)
     * @param relevantBookIds set of all relevant book IDs in the corpus for this query
     * @param k ranking cutoff threshold (e.g. 5 or 10)
     * @return EvaluationResult containing Precision@K, Recall@K, and Reciprocal Rank (RR@K)
     */
    public EvaluationResult evaluate(List<Long> rankedBookIds, Set<Long> relevantBookIds, int k) {
        if (k <= 0) {
            return new EvaluationResult(0.0, 0.0, 0.0);
        }

        List<Long> topK = (rankedBookIds == null) ? List.of() : rankedBookIds.stream().limit(k).toList();

        long relevantInTopK = 0;
        if (relevantBookIds != null && !relevantBookIds.isEmpty()) {
            relevantInTopK = topK.stream().filter(relevantBookIds::contains).count();
        }

        double precisionAtK = (double) relevantInTopK / k;

        double recallAtK = 0.0;
        if (relevantBookIds != null && !relevantBookIds.isEmpty()) {
            recallAtK = (double) relevantInTopK / relevantBookIds.size();
        }

        double reciprocalRank = 0.0;
        if (topK != null && relevantBookIds != null && !relevantBookIds.isEmpty()) {
            for (int rank = 1; rank <= topK.size(); rank++) {
                Long bookId = topK.get(rank - 1);
                if (relevantBookIds.contains(bookId)) {
                    reciprocalRank = 1.0 / rank;
                    break;
                }
            }
        }

        return new EvaluationResult(precisionAtK, recallAtK, reciprocalRank);
    }

    /**
     * Evaluates a suite of queries and aggregates metrics across all queries.
     *
     * @param allRankedBookIds list of ranked results for each query
     * @param allRelevantBookIds list of relevant sets for each query
     * @param k ranking cutoff threshold
     * @return Aggregate EvaluationResult containing Mean Precision@K, Mean Recall@K, and MRR@K
     */
    public EvaluationResult evaluateSuite(List<List<Long>> allRankedBookIds,
                                          List<Set<Long>> allRelevantBookIds,
                                          int k) {
        if (allRankedBookIds == null || allRelevantBookIds == null || allRankedBookIds.isEmpty()) {
            return new EvaluationResult(0.0, 0.0, 0.0);
        }

        int queryCount = Math.min(allRankedBookIds.size(), allRelevantBookIds.size());
        if (queryCount == 0) {
            return new EvaluationResult(0.0, 0.0, 0.0);
        }

        double sumPrecision = 0.0;
        double sumRecall = 0.0;
        double sumReciprocalRank = 0.0;

        for (int i = 0; i < queryCount; i++) {
            EvaluationResult single = evaluate(allRankedBookIds.get(i), allRelevantBookIds.get(i), k);
            sumPrecision += single.precisionAtK();
            sumRecall += single.recallAtK();
            sumReciprocalRank += single.mrr();
        }

        return new EvaluationResult(
                sumPrecision / queryCount,
                sumRecall / queryCount,
                sumReciprocalRank / queryCount
        );
    }
}
