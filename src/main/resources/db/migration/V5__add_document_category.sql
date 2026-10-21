ALTER TABLE documents
ADD COLUMN category VARCHAR(100);

CREATE INDEX idx_documents_category
ON documents (category);
