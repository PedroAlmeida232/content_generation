CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    context_id UUID REFERENCES user_contexts(id),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    style VARCHAR(100),
    slide_count INTEGER DEFAULT 5,
    status VARCHAR(50) DEFAULT 'draft',
    job_id VARCHAR(255),
    created_at TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_projects_user_id ON projects(user_id);
CREATE INDEX idx_projects_status ON projects(status);
