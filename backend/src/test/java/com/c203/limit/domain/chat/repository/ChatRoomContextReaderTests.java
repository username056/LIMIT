package com.c203.limit.domain.chat.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ChatRoomContextReaderTests {

    @Test
    @DisplayName("사진과 영상은 파일명 대신 무엇을 보냈는지로 적는다")
    void describesMediaInsteadOfFileName() {
        assertThat(ChatRoomContextReader.lastMessagePreview("IMAGE", "IMG_2381.jpeg"))
                .isEqualTo("사진을 보냈습니다.");
        assertThat(ChatRoomContextReader.lastMessagePreview("VIDEO", "clip.mp4"))
                .isEqualTo("영상을 보냈습니다.");
    }

    @Test
    @DisplayName("여러 줄로 쓴 말은 한 줄로 눌러 적는다")
    void flattensMultilineText() {
        assertThat(ChatRoomContextReader.lastMessagePreview("TEXT", "안녕하세요\n\n오늘 가능할까요?"))
                .isEqualTo("안녕하세요 오늘 가능할까요?");
    }

    @Test
    @DisplayName("긴 글은 100자로 자른다")
    void truncatesLongText() {
        String preview = ChatRoomContextReader.lastMessagePreview("TEXT", "가".repeat(300));

        // content가 LONGTEXT라, 자르지 않으면 방 수만큼 응답이 불어납니다.
        assertThat(preview).hasSize(101).endsWith("…");
    }

    @Test
    @DisplayName("100자 이하는 그대로 두고 말줄임표를 붙이지 않는다")
    void keepsShortTextAsIs() {
        assertThat(ChatRoomContextReader.lastMessagePreview("TEXT", "가".repeat(100)))
                .hasSize(100)
                .doesNotContain("…");
    }

    @Test
    @DisplayName("주고받은 말이 없거나 내용이 비면 null을 준다")
    void returnsNullWhenNothingToShow() {
        assertThat(ChatRoomContextReader.lastMessagePreview(null, null)).isNull();
        assertThat(ChatRoomContextReader.lastMessagePreview("TEXT", null)).isNull();
        assertThat(ChatRoomContextReader.lastMessagePreview("TEXT", "   ")).isNull();
    }
}
