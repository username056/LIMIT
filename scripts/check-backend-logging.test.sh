#!/bin/sh
set -eu

root_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
test_root=$(mktemp -d "${TMPDIR:-/tmp}/backend-logging-guard.XXXXXX")
trap 'rm -rf -- "$test_root"' EXIT HUP INT TERM

new_repo() {
  repo=$1
  mkdir -p "$repo/backend/src/main/java/com/c203/limit/domain/sample/service"
  git -C "$repo" init -q
  git -C "$repo" config user.name test
  git -C "$repo" config user.email test@example.invalid
  printf '%s\n' baseline > "$repo/README.md"
  git -C "$repo" add README.md
  git -C "$repo" commit -q -m baseline
}

expect_failure() {
  repo=$1
  base=$2
  if (cd "$repo" && sh "$root_dir/scripts/check-backend-logging.sh" "$base" HEAD >/dev/null 2>&1); then
    echo "expected logging guard failure: $repo" >&2
    exit 1
  fi
}

expect_success() {
  repo=$1
  base=$2
  (cd "$repo" && sh "$root_dir/scripts/check-backend-logging.sh" "$base" HEAD >/dev/null)
}

missing_repo="$test_root/missing"
new_repo "$missing_repo"
missing_base=$(git -C "$missing_repo" rev-parse HEAD)
cat > "$missing_repo/backend/src/main/java/com/c203/limit/domain/sample/service/SampleService.java" <<'EOF'
package com.c203.limit.domain.sample.service;

import org.springframework.stereotype.Service;

@Service
public class SampleService {
    public void run() {}
}
EOF
git -C "$missing_repo" add .
git -C "$missing_repo" commit -q -m candidate
expect_failure "$missing_repo" "$missing_base"

info_repo="$test_root/info"
new_repo "$info_repo"
info_base=$(git -C "$info_repo" rev-parse HEAD)
cat > "$info_repo/backend/src/main/java/com/c203/limit/domain/sample/service/SampleService.java" <<'EOF'
package com.c203.limit.domain.sample.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SampleService {
    private static final Logger log = LoggerFactory.getLogger(SampleService.class);

    public void run() {
        log.info("sample completed");
    }
}
EOF
git -C "$info_repo" add .
git -C "$info_repo" commit -q -m candidate
expect_success "$info_repo" "$info_base"

debug_repo="$test_root/debug"
new_repo "$debug_repo"
debug_base=$(git -C "$debug_repo" rev-parse HEAD)
cat > "$debug_repo/backend/src/main/java/com/c203/limit/domain/sample/service/SampleService.java" <<'EOF'
package com.c203.limit.domain.sample.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SampleService {
    public void run() {
        log.debug("sample completed");
    }
}
EOF
git -C "$debug_repo" add .
git -C "$debug_repo" commit -q -m candidate
expect_failure "$debug_repo" "$debug_base"

comment_repo="$test_root/comment"
new_repo "$comment_repo"
comment_base=$(git -C "$comment_repo" rev-parse HEAD)
cat > "$comment_repo/backend/src/main/java/com/c203/limit/domain/sample/service/SampleService.java" <<'EOF'
package com.c203.limit.domain.sample.service;

import org.springframework.stereotype.Service;

@Service
public class SampleService {
    // private static final Logger log = LoggerFactory.getLogger(SampleService.class);

    public void run() {
        String fakeLog = "log.info(should not pass)";
        // log.info("sample completed");
    }
}
EOF
git -C "$comment_repo" add .
git -C "$comment_repo" commit -q -m candidate
expect_failure "$comment_repo" "$comment_base"

dto_repo="$test_root/dto"
new_repo "$dto_repo"
dto_base=$(git -C "$dto_repo" rev-parse HEAD)
mkdir -p "$dto_repo/backend/src/main/java/com/c203/limit/domain/sample/dto"
cat > "$dto_repo/backend/src/main/java/com/c203/limit/domain/sample/dto/SampleResponse.java" <<'EOF'
package com.c203.limit.domain.sample.dto;

public record SampleResponse(long id) {}
EOF
git -C "$dto_repo" add .
git -C "$dto_repo" commit -q -m candidate
expect_success "$dto_repo" "$dto_base"

echo "backend logging guard tests passed"
