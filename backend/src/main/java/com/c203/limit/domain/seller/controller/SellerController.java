package com.c203.limit.domain.seller.controller;
import org.springframework.http.ResponseEntity; import org.springframework.web.bind.annotation.RestController; import org.springframework.web.multipart.MultipartFile;
import com.c203.limit.domain.seller.dto.request.UpdateSellerApplicationRequest; import com.c203.limit.domain.seller.service.SellerService; import com.c203.limit.global.exception.*; import com.c203.limit.global.response.ApiResponse; import com.c203.limit.global.security.CurrentUser; import com.fasterxml.jackson.databind.*;
@RestController public class SellerController implements SellerApi {
 private final SellerService service; private final CurrentUser currentUser; private final ObjectMapper mapper;
 public SellerController(SellerService service,CurrentUser currentUser,ObjectMapper mapper){this.service=service;this.currentUser=currentUser;this.mapper=mapper;}
 @Override public ResponseEntity<Void> sellerapp01(Object body){var j=json(body);return response(ResponseEntity.status(201).body(ApiResponse.ok(service.create(currentUser.memberId(),required(j,"sellerType")))));}
 @Override public ResponseEntity<Void> sellerapp02(Integer page,Integer size){return response(ResponseEntity.ok(ApiResponse.ok(service.list(currentUser.memberId(),page,size))));}
 @Override public ResponseEntity<Void> sellerapp03(Long id){return response(ResponseEntity.ok(ApiResponse.ok(service.detail(currentUser.memberId(),id))));}
 @Override public ResponseEntity<Void> sellerapp04(Long id,Object body){var j=json(body);var r=new UpdateSellerApplicationRequest(required(j,"applicantName"),required(j,"applicantEmail"),required(j,"applicantPhone"),required(j,"countryCode"),optional(j,"businessName"),optional(j,"businessNumber"),optional(j,"businessAddress"),required(j,"settlementBankName"),required(j,"settlementAccount"),required(j,"settlementAccountHolder"),required(j,"plannedCategory"),j.path("termsAgreed").asBoolean(false));return response(ResponseEntity.ok(ApiResponse.ok(service.update(currentUser.memberId(),id,r))));}
 @Override public ResponseEntity<Void> sellerapp05(Long id,MultipartFile[] files){return response(ResponseEntity.status(201).body(ApiResponse.ok(service.upload(currentUser.memberId(),id,files))));}
 @Override public ResponseEntity<Void> sellerapp06(Long id,Long documentId){service.deleteDocument(currentUser.memberId(),id,documentId);return ResponseEntity.noContent().build();}
 @Override public ResponseEntity<Void> sellerapp07(Long id,Object body){return response(ResponseEntity.ok(ApiResponse.ok(service.submit(currentUser.memberId(),id))));}
 @Override public ResponseEntity<Void> sellerapp08(Long id,Object body){return response(ResponseEntity.ok(ApiResponse.ok(service.cancel(currentUser.memberId(),id))));}
 @Override public ResponseEntity<Void> seller01(){return response(ResponseEntity.ok(ApiResponse.ok(service.profile(currentUser.memberId()))));}
 private JsonNode json(Object body){if(body==null)throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);return mapper.valueToTree(body);} private String required(JsonNode j,String f){String v=optional(j,f);if(v==null||v.isBlank())throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);return v;} private String optional(JsonNode j,String f){var v=j.get(f);return v==null||v.isNull()?null:v.asText();}
 @SuppressWarnings({"unchecked","rawtypes"}) private ResponseEntity<Void> response(ResponseEntity<?> source){return(ResponseEntity)source;}
}
