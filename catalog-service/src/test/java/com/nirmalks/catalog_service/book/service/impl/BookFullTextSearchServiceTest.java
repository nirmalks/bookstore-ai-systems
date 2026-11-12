package com.nirmalks.catalog_service.book.service.impl;

import com.nirmalks.catalog_service.book.dto.BookDto;
import com.nirmalks.catalog_service.book.entity.Book;
import com.nirmalks.catalog_service.book.repository.BookRepository;
import com.nirmalks.catalog_service.book.service.BookServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookFullTextSearchServiceTest {

	@Mock
	private BookRepository bookRepository;

	@InjectMocks
	private BookServiceImpl bookService;

	@Test
	@DisplayName("Should route non-blank search to full text search with trimming and filters")
	void shouldSearchBooksFullTextSuccessfully() {
		// Given
		String search = "  inspiring dreams  ";
		String genre = "Fiction";
		LocalDate startDate = LocalDate.of(2020, 1, 1);
		LocalDate endDate = LocalDate.of(2025, 1, 1);
		Double minPrice = 100.0;
		Double maxPrice = 500.0;
		String sortBy = "title";
		String sortOrder = "asc";
		int page = 0;
		int size = 10;

		Book book = new Book();
		book.setId(1L);
		book.setTitle("The Alchemist");
		book.setIsbn("978-3-16-148410-0");
		book.setPrice(499.99);
		book.setStock(10);
		book.setPublishedDate(LocalDate.of(2024, 1, 1));
		book.setDescription("A philosophical novel by Paulo Coelho, inspiring readers to follow their dreams.");
		book.setAuthors(Collections.emptyList());
		book.setGenres(Collections.emptyList());

		Pageable pageable = PageRequest.of(page, size);
		Page<Book> bookPage = new PageImpl<>(List.of(book), pageable, 1);

		when(bookRepository.searchBooksFullText(eq("inspiring dreams"), eq(genre), eq(startDate), eq(endDate),
				eq(minPrice), eq(maxPrice), eq(pageable)))
				.thenReturn(bookPage);

		// When
		Page<BookDto> result = bookService.getFilteredBooks(search, genre, startDate, endDate, minPrice, maxPrice,
				sortBy, sortOrder, page, size);

		// Then
		assertThat(result).isNotNull();
		assertThat(result.getContent()).hasSize(1);
		assertThat(result.getContent().get(0).title()).isEqualTo("The Alchemist");
		verify(bookRepository).searchBooksFullText(eq("inspiring dreams"), eq(genre), eq(startDate), eq(endDate),
				eq(minPrice), eq(maxPrice), eq(pageable));
	}

	@Test
	@DisplayName("Should fall back to standard specification when search query is null or blank")
	void shouldFallbackToSpecificationWhenSearchIsBlank() {
		// Given
		Pageable pageable = PageRequest.of(0, 10);
		Page<Book> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);

		when(bookRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);

		// When
		Page<BookDto> resultNull = bookService.getFilteredBooks(null, "Fiction", null, null, null, null, "title", "asc", 0, 10);
		Page<BookDto> resultBlank = bookService.getFilteredBooks("   ", "Fiction", null, null, null, null, "title", "asc", 0, 10);

		// Then
		assertThat(resultNull).isNotNull();
		assertThat(resultBlank).isNotNull();
	}

	@Test
	@DisplayName("Should reject overly long queries")
	void shouldRejectOverlyLongQuery() {
		String longQuery = "a".repeat(256);
		assertThatThrownBy(() -> bookService.getFilteredBooks(longQuery, null, null, null, null, null, "title", "asc", 0, 10))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("maximum length");
	}
}
