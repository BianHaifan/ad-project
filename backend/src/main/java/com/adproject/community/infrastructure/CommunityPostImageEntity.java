package com.adproject.community.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "community_post_images")
public class CommunityPostImageEntity {
    @Id @Column(length = 36, columnDefinition = "char(36)") private String id;
    @Column(name = "post_id", nullable = false, length = 36, columnDefinition = "char(36)") private String postId;
    @Column(name = "position_index", nullable = false) private int position;
    @Column(name = "content_type", nullable = false, length = 32) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Lob @Column(nullable = false, columnDefinition = "LONGBLOB") private byte[] content;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected CommunityPostImageEntity() {}
    public CommunityPostImageEntity(String id, String postId, int position, String contentType, byte[] content, Instant createdAt) {
        this.id=id; this.postId=postId; this.position=position; this.contentType=contentType;
        this.sizeBytes=content.length; this.content=content.clone(); this.createdAt=createdAt;
    }
    public String getId(){return id;} public String getPostId(){return postId;} public int getPosition(){return position;}
    public String getContentType(){return contentType;} public long getSizeBytes(){return sizeBytes;}
    public byte[] getContent(){return content.clone();}
}
