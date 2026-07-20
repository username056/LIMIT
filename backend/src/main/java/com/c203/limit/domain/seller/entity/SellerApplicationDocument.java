package com.c203.limit.domain.seller.entity;
import java.time.OffsetDateTime;
import jakarta.persistence.*;
@Entity @Table(name="seller_application_document")
public class SellerApplicationDocument {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) @Column(name="document_id") private Long id;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="application_id") private SellerApplication application;
 @Column(name="document_type",nullable=false) private String documentType; @Column(name="storage_key",nullable=false,length=500) private String storageKey;
 @Column(name="original_filename",nullable=false) private String originalFilename; @Column(name="content_type") private String contentType;
 @Column(name="file_size",nullable=false) private long fileSize; @Column(name="uploaded_at",nullable=false) private OffsetDateTime uploadedAt;
 protected SellerApplicationDocument(){}
 public static SellerApplicationDocument create(SellerApplication a,String type,String key,String name,String contentType,long size){var d=new SellerApplicationDocument();d.application=a;d.documentType=type;d.storageKey=key;d.originalFilename=name;d.contentType=contentType;d.fileSize=size;d.uploadedAt=OffsetDateTime.now();return d;}
 public Long getId(){return id;} public SellerApplication getApplication(){return application;} public String getDocumentType(){return documentType;} public String getStorageKey(){return storageKey;} public String getOriginalFilename(){return originalFilename;} public String getContentType(){return contentType;} public long getFileSize(){return fileSize;} public OffsetDateTime getUploadedAt(){return uploadedAt;}
}
