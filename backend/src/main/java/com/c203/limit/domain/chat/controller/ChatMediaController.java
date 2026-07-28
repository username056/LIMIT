package com.c203.limit.domain.chat.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.c203.limit.domain.chat.dto.response.ChatMediaResponse;
import com.c203.limit.domain.chat.service.ChatMediaService;
import com.c203.limit.domain.chat.service.ChatMediaService.MediaDownload;
import com.c203.limit.global.response.ApiResponse;
import com.c203.limit.global.security.CurrentUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "04. 채팅")
public class ChatMediaController {
    private final ChatMediaService mediaService;
    private final CurrentUser currentUser;

    public ChatMediaController(ChatMediaService mediaService, CurrentUser currentUser) {
        this.mediaService = mediaService;
        this.currentUser = currentUser;
    }

    @Operation(
            summary = "채팅 이미지·영상 업로드",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "201", description = "파일 저장 완료"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403", description = "CHAT_ROOM_ACCESS_DENIED"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "422", description = "CHAT_MEDIA_INVALID")
    })
    @PostMapping(
            path = "/api/v1/chat-rooms/{roomId}/media",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ChatMediaResponse>> upload(
            @PathVariable Long roomId, @RequestPart MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(mediaService.upload(roomId, currentUser.memberId(), file)));
    }

    @Operation(
            summary = "채팅 이미지·영상 조회",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200", description = "파일 반환"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403", description = "CHAT_ROOM_ACCESS_DENIED"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404", description = "CHAT_MEDIA_NOT_FOUND")
    })
    @GetMapping("/api/v1/chat-media/{mediaId}/content")
    public ResponseEntity<org.springframework.core.io.Resource> download(@PathVariable Long mediaId) {
        MediaDownload download = mediaService.download(mediaId, currentUser.memberId());
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(download.mimeType()))
                .body(download.resource());
    }
}
