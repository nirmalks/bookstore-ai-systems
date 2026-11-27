package com.nirmalks.catalog_service.book.benchmark;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Benchmark dataset fixture containing 18 curated books across several genres
 * and 10 representative information retrieval queries.
 */
public class BenchmarkDataset {

    public record BenchmarkBook(
            String key,
            String title,
            String description,
            String isbn,
            double price,
            int stockQuantity,
            int purchaseCount
    ) {}

    public record BenchmarkQuery(
            String id,
            String queryText,
            Set<String> relevantBookKeys,
            String scenario,
            String expectedBehavior
    ) {}

    public static final List<BenchmarkBook> BOOKS = List.of(
            new BenchmarkBook("B01", "Cloud Architecture Patterns",
                    "Comprehensive design patterns and architectural solutions for resilient cloud systems.",
                    "978-0-01-000001-1", 49.99, 100, 150),

            new BenchmarkBook("B02", "Enterprise Cloud Architecture",
                    "Building scalable enterprise applications and infrastructure in multi-cloud environments.",
                    "978-0-01-000002-2", 54.99, 80, 40),

            new BenchmarkBook("B03", "Cloud Native Architecture",
                    "Modern microservices, containers, and serverless architectures on modern cloud platforms.",
                    "978-0-01-000003-3", 45.00, 60, 10),

            new BenchmarkBook("B04", "Architecting Distributed Systems",
                    "Patterns for building high-availability distributed systems and fault-tolerant computing.",
                    "978-0-01-000004-4", 59.99, 50, 80),

            new BenchmarkBook("B05", "Software Systems Architecture",
                    "Foundations of software engineering architecture and large-scale system decomposition.",
                    "978-0-01-000005-5", 52.50, 70, 20),

            new BenchmarkBook("B06", "The Art of Computer Programming",
                    "Fundamental algorithms and mathematical structures in the art of computing.",
                    "978-0-01-000006-6", 89.99, 40, 300),

            new BenchmarkBook("B07", "The Art of Systems Architecture",
                    "Mastering the heuristics and artistic discipline of complex systems design.",
                    "978-0-01-000007-7", 65.00, 35, 25),

            new BenchmarkBook("B08", "The Alchemist",
                    "A philosophical novel about following dreams and discovering one's path.",
                    "978-0-01-000008-8", 16.99, 200, 500),

            new BenchmarkBook("B09", "Harry Potter and the Sorcerer's Stone",
                    "The first book introducing the magical world of Hogwarts.",
                    "978-0-01-000009-9", 19.99, 250, 600),

            new BenchmarkBook("B10", "Building Microservices",
                    "Designing fine-grained distributed systems and decoupled services.",
                    "978-0-01-000010-0", 48.00, 90, 220),

            new BenchmarkBook("B11", "Clean Code: A Handbook of Agile Software Craftsmanship",
                    "Writing maintainable, professional code through craftsmanship and discipline.",
                    "978-0-01-000011-1", 44.99, 120, 450),

            new BenchmarkBook("B12", "The Software Craftsman",
                    "Professionalism, pragmatism, and clean code principles for software developers.",
                    "978-0-01-000012-2", 39.99, 85, 60),

            new BenchmarkBook("B13", "Python Programming Fundamentals",
                    "Hands-on guide to Python scripting, data structures, and algorithmic programming.",
                    "978-0-01-000013-3", 35.00, 110, 180),

            new BenchmarkBook("B14", "Fluent Python",
                    "Clear, concise, and effective programming techniques in idiomatic Python.",
                    "978-0-01-000014-4", 55.00, 75, 95),

            new BenchmarkBook("B15", "Database Internals",
                    "A deep dive into how distributed database systems and storage engines work.",
                    "978-0-01-000015-5", 62.00, 65, 110),

            new BenchmarkBook("B16", "High Performance MySQL",
                    "Optimization, indexing, replication, and scaling for relational database systems.",
                    "978-0-01-000016-6", 58.00, 55, 75),

            new BenchmarkBook("B17", "System Design Interview",
                    "An insider's guide to passing large scale distributed systems design interviews.",
                    "978-0-01-000017-7", 42.00, 150, 350),

            new BenchmarkBook("B18", "Designing Data-Intensive Applications",
                    "The big ideas behind reliable, scalable, and maintainable systems architecture.",
                    "978-0-01-000018-8", 50.00, 180, 500)
    );

    public static final List<BenchmarkQuery> QUERIES = List.of(
            new BenchmarkQuery("Q1", "cloud architecture",
                    Set.of("B01", "B02", "B03"),
                    "Keyword matching",
                    "Retrieves all books with exact cloud architecture matches in title and description."),

            new BenchmarkQuery("Q2", "architecting systems",
                    Set.of("B01", "B04", "B05", "B07", "B18"),
                    "Stemming",
                    "Stemming normalizes 'architecting' -> 'architectur', matching Architecture and Systems."),

            new BenchmarkQuery("Q3", "the art of computing",
                    Set.of("B06", "B07"),
                    "Stop-word handling",
                    "Removes 'the' and 'of', matching 'art' and 'comput' / 'computing'."),

            new BenchmarkQuery("Q4", "finding life purpose and personal destiny",
                    Set.of("B08"),
                    "Vocabulary mismatch",
                    "Lexical search fails because target book The Alchemist uses 'dreams', 'philosophical', 'path' without lexical overlap."),

            new BenchmarkQuery("Q5", "witchcraft wizard spells apprentice",
                    Set.of("B09"),
                    "Contextual vocabulary mismatch",
                    "Lexical search fails because Harry Potter description uses 'magical world of Hogwarts' without exact query keywords."),

            new BenchmarkQuery("Q6", "building microservices distributed",
                    Set.of("B04", "B10"),
                    "Multi-term matching",
                    "Retrieves distributed systems and microservices engineering guides."),

            new BenchmarkQuery("Q7", "clean code craftsmanship",
                    Set.of("B11", "B12"),
                    "Title vs Description weighting",
                    "Demonstrates title match weight A vs description match weight B."),

            new BenchmarkQuery("Q8", "python programming",
                    Set.of("B13", "B14"),
                    "Programming language topic",
                    "Matches Python language books based on lexical match."),

            new BenchmarkQuery("Q9", "database systems",
                    Set.of("B15", "B16"),
                    "Database systems",
                    "Matches storage engines and relational database architecture."),

            new BenchmarkQuery("Q10", "system design interview",
                    Set.of("B17"),
                    "Precise composite query matching",
                    "Exact title match with high precision.")
    );
}
