-- 회원 프로필 사진.
--
-- URL이 아니라 S3 오브젝트 키를 담는다. listing_image가 cdn_url을 들고 있다가 버킷·CDN이
-- 바뀌면서 그 값이 통째로 죽었고, 채팅 목록이 사진을 한 번도 못 보여 준 일이 있었다.
-- 키만 두고 URL은 읽을 때 MediaUrlResolver가 만들면 그런 일이 생기지 않는다.
--
-- NULL이면 사진을 올리지 않은 회원이다. 화면은 닉네임 첫 글자로 대신한다.
ALTER TABLE user_account
    ADD COLUMN profile_image_key VARCHAR(500) NULL AFTER phone;
