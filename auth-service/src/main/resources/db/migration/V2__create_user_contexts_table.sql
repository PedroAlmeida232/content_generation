CREATE TABLE user_contexts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    logo_url TEXT,
    color_palette JSONB,
    default_images JSONB,
    tone VARCHAR(100),
    created_at TIMESTAMP DEFAULT now()
);

CREATE INDEX idx_user_contexts_user_id ON user_contexts(user_id);
