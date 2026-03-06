---
name: Smart Commit
description: Generate intelligent commit messages in Korean following Conventional Commits.
---

# Smart Commit Guidelines

This skill helps the agent generate high-quality commit messages based on project rules.

## Core Rules
1.  **Language**: MUST be in **Korean (한국어)**. No exceptions.
2.  **Format**: Follow **Conventional Commits** (`type: subject`).
3.  **Context Aware**: content must be based on actual `git diff` changes.

## Conventional Commits Types
| Type | Description |
| :--- | :--- |
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 수정 |
| `style` | 코드 포맷팅, 세미콜론 누락 등 (비즈니스 로직 변경 없음) |
| `refactor` | 코드 리팩토링 (기능 변경 없음) |
| `test` | 테스트 코드 추가 또는 수정 |
| `chore` | 빌드 업무 수정, 패키지 매니저 설정 등 |

## Examples

### Good ✅
- `feat: 사용자 로그인 API 추가`
- `fix: 결제 페이지 크래시 문제 해결`
- `docs: README.md 설치 가이드 업데이트`
- `style: 코드 포맷팅 (Prettier 적용)`
- `refactor: 중복된 유틸리티 함수 제거`

### Bad ❌
- `Update code` (너무 모호함)
- `Add login feature` (영어로 작성됨)
- `Login stuff` (형식 위반)

## Steps for the Agent
1.  Analyze the changes using `git diff` or by checking modified files.
2.  Determine the primary intent (feature, fix, chore, etc.).
3.  Draft a concise subject line in Korean.
4.  If necessary, add a body detailed explanation in Korean.