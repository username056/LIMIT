package com.c203.limit.domain.seller.client;
import org.springframework.web.multipart.MultipartFile;
public interface SellerDocumentStorage { String upload(Long applicationId,MultipartFile file); void delete(String storageKey); }
