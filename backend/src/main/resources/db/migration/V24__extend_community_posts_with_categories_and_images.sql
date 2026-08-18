ALTER TABLE community_posts ADD COLUMN category VARCHAR(32) NOT NULL DEFAULT 'GENERAL';
CREATE INDEX idx_community_posts_category_created ON community_posts (category, created_at, id);

CREATE TABLE community_post_images (
    id CHAR(36) NOT NULL,
    post_id CHAR(36) NOT NULL,
    position_index INT NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content LONGBLOB NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_community_post_images_position UNIQUE (post_id, position_index),
    CONSTRAINT fk_community_post_images_post FOREIGN KEY (post_id) REFERENCES community_posts (id),
    CONSTRAINT chk_community_post_images_position CHECK (position_index BETWEEN 0 AND 3),
    CONSTRAINT chk_community_post_images_size CHECK (size_bytes BETWEEN 1 AND 5242880)
);
CREATE INDEX idx_community_post_images_post ON community_post_images (post_id, position_index);
