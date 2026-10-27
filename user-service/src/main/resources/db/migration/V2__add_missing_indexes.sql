-- Index for user_id in address table to optimize user profile loading and cascades
CREATE INDEX idx_address_user_id ON address(user_id);
