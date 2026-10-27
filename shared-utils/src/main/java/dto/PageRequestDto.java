package dto;

public record PageRequestDto(Integer page, Integer size, String sortKey, String sortOrder) {
	public PageRequestDto {
		// Handle defaults or logic if the inputs are null/invalid
		if (page == null || page < 0)
			page = 0;
		if (size == null || size <= 0)
			size = 10;
		if (sortKey == null || sortKey.isBlank())
			sortKey = "id";
		if (sortOrder == null || sortOrder.isBlank())
			sortOrder = "asc";
	}

}
