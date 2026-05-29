CREATE TABLE project_slides (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    slide_order INTEGER NOT NULL,
    image_url TEXT,
    caption TEXT,
    prompt_used TEXT,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_slides_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT uk_project_slides_project_order
        UNIQUE (project_id, slide_order)
);

CREATE INDEX idx_project_slides_project
    ON project_slides(project_id, slide_order);
