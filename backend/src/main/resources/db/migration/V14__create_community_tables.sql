CREATE TABLE community_posts (
    id CHAR(36) NOT NULL,
    author_id CHAR(36) NOT NULL,
    body TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_community_posts_author FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT chk_community_posts_body CHECK (CHAR_LENGTH(body) BETWEEN 1 AND 2000)
);

CREATE INDEX idx_community_posts_created_id ON community_posts (created_at, id);

CREATE TABLE community_post_likes (
    post_id CHAR(36) NOT NULL,
    user_id CHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (post_id, user_id),
    CONSTRAINT fk_community_post_likes_post FOREIGN KEY (post_id) REFERENCES community_posts (id),
    CONSTRAINT fk_community_post_likes_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE community_comments (
    id CHAR(36) NOT NULL,
    post_id CHAR(36) NOT NULL,
    author_id CHAR(36) NOT NULL,
    body TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_community_comments_post FOREIGN KEY (post_id) REFERENCES community_posts (id),
    CONSTRAINT fk_community_comments_author FOREIGN KEY (author_id) REFERENCES users (id),
    CONSTRAINT chk_community_comments_body CHECK (CHAR_LENGTH(body) BETWEEN 1 AND 500)
);

CREATE INDEX idx_community_comments_post_created_id ON community_comments (post_id, created_at, id);
