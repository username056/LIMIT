package com.c203.limit.domain.member.service;

import com.c203.limit.domain.member.dto.request.CompleteProfileImageRequest;
import com.c203.limit.domain.member.dto.request.CreateProfileImageUploadUrlRequest;
import com.c203.limit.domain.member.dto.response.ProfileImageResponse;
import com.c203.limit.domain.member.dto.response.ProfileImageUploadUrlResponse;
import com.c203.limit.domain.member.entity.Member;
import com.c203.limit.domain.member.repository.MemberRepository;
import com.c203.limit.domain.product.storage.MediaObjectStorage;
import com.c203.limit.domain.product.storage.MediaUrlResolver;
import com.c203.limit.domain.product.storage.S3MediaProperties;
import com.c203.limit.global.exception.BusinessException;
import com.c203.limit.global.exception.ErrorCode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 프로필 사진 업로드.
 *
 * <p>상품 이미지(ListingImageUploadService)와 달리 임시 키 → 최종 키로 옮기는 업로드 세션을 두지 않는다. 상품 사진은 열 장까지 순서·대표 여부가
 * 얽히고 판매글 소유 검증이 필요해 그 장치가 필요했다. 프로필은 한 장뿐이고 주인이 곧 요청자라, 최종 키를 서버가 정해 그 키로만 쓸 수 있는 URL을 내주면 충분하다.
 *
 * <p>키를 서버가 만들기 때문에 회원이 남의 자리에 덮어쓸 수 없다. 완료 통보 때 S3에 실제로 올라왔는지 head로 확인한다 — 확인하지 않으면 올리지 않고 완료만 불러
 * 깨진 사진이 박힌다.
 *
 * <p>이 서비스가 쓰는 members/ prefix는 런타임 자격 증명의 S3 정책에도 들어 있어야 한다. 정책에 없으면 API·DB·화면은 모두 정상인데 S3 PUT만
 * 403 AccessDenied로 막혀, 코드를 봐도 원인이 보이지 않는다. 허용 prefix 목록은 docs/runbook/s3-media.md에 있다.
 */
@Service
public class MemberProfileImageService {
    private static final Logger log = LoggerFactory.getLogger(MemberProfileImageService.class);

    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;

    private final MemberRepository memberRepository;
    private final MediaObjectStorage storage;
    private final S3MediaProperties properties;
    private final MediaUrlResolver mediaUrlResolver;

    public MemberProfileImageService(
            MemberRepository memberRepository,
            MediaObjectStorage storage,
            S3MediaProperties properties,
            MediaUrlResolver mediaUrlResolver) {
        this.memberRepository = memberRepository;
        this.storage = storage;
        this.properties = properties;
        this.mediaUrlResolver = mediaUrlResolver;
    }

    @Transactional(readOnly = true)
    public ProfileImageUploadUrlResponse createUploadUrl(
            Long memberId, CreateProfileImageUploadUrlRequest request) {
        requireConfigured();
        String contentType = normalized(request.getContentType());
        if (!IMAGE_TYPES.contains(contentType) || request.getFileSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_INVALID);
        }

        // 키에 UUID를 넣어 매번 새 주소가 된다. 같은 키를 덮어쓰면 CDN·브라우저가 옛 사진을
        // 계속 보여 준다.
        String objectKey =
                "members/" + memberId + "/profile/" + UUID.randomUUID() + extension(contentType);
        var url =
                storage.presignPut(
                        properties.bucket(),
                        objectKey,
                        contentType,
                        request.getFileSize(),
                        properties.uploadTtl());
        return new ProfileImageUploadUrlResponse(
                objectKey,
                url.toString(),
                OffsetDateTime.now(ZoneOffset.UTC).plus(properties.uploadTtl()),
                Map.of("Content-Type", contentType));
    }

    @Transactional
    public ProfileImageResponse complete(Long memberId, CompleteProfileImageRequest request) {
        requireConfigured();
        Member member = member(memberId);
        String objectKey = request.getObjectKey();
        // 내가 발급받은 자리인지 확인한다. 키를 손으로 바꿔 남의 사진을 자기 것으로 등록하는 것을 막는다.
        if (objectKey == null || !objectKey.startsWith("members/" + memberId + "/profile/")) {
            // 화면은 서버가 준 키를 그대로 돌려주므로, 여기 걸리는 것은 손으로 바꾼 요청이다.
            log.warn("profile image key rejected: memberId={}", memberId);
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_INVALID);
        }
        try {
            storage.head(properties.bucket(), objectKey);
        } catch (RuntimeException exception) {
            log.warn(
                    "profile image not found in storage: memberId={}, reason={}",
                    memberId,
                    exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_INVALID);
        }

        String previousKey = member.getProfileImageKey();
        member.changeProfileImage(objectKey);
        deleteQuietly(previousKey);
        log.info(
                "member profile image changed: memberId={}, replaced={}",
                memberId,
                previousKey != null);
        return new ProfileImageResponse(mediaUrlResolver.resolve(objectKey, null));
    }

    @Transactional
    public ProfileImageResponse remove(Long memberId) {
        Member member = member(memberId);
        String previousKey = member.getProfileImageKey();
        member.changeProfileImage(null);
        deleteQuietly(previousKey);
        log.info(
                "member profile image removed: memberId={}, hadImage={}",
                memberId,
                previousKey != null);
        return new ProfileImageResponse(null);
    }

    /** 옛 사진은 지우되, 실패해도 프로필 변경 자체는 되돌리지 않는다. */
    private void deleteQuietly(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) return;
        if (properties.bucket() == null || properties.bucket().isBlank()) return;
        try {
            storage.delete(properties.bucket(), objectKey);
        } catch (RuntimeException exception) {
            // 저장소에 남은 파일은 나중에 정리한다. 회원이 사진을 바꾸는 것을 막을 이유가 없다.
            // 다만 어떤 파일이 남았는지는 남겨 둔다. 로그가 없으면 찾을 방법이 없다.
            log.warn("orphan profile image left in storage: objectKey={}", objectKey);
        }
    }

    private Member member(Long memberId) {
        return memberRepository
                .findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void requireConfigured() {
        if (properties.bucket() == null || properties.bucket().isBlank()) {
            throw new BusinessException(ErrorCode.MEDIA_UPLOAD_INVALID);
        }
    }

    private static String normalized(String contentType) {
        return contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
    }

    private static String extension(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
