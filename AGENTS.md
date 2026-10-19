## Coding convention

이 프로젝트의 모든 코드 작성과 수정은 `docs/coding_convention.md`를 따른다.

- 코드를 작성하거나 수정하기 전에 `docs/coding_convention.md`와 `docs/refactoring_plan.md`를 읽는다.
- 기존 비즈니스 동작, API 계약, DB 스키마, 외부 데이터 형식은 바꾸지 않는다. 바꿔야 하면 작업을 멈추고 묻는다.
- 스타일 규칙은 가독성을 우선해 적용한다. 규칙을 지키려고 불필요한 추상화, 메서드, 클래스, 주석을 만들지 않는다.
- 요청 범위 밖의 규칙 위반은 수정하지 말고 보고한다.
- 실행하지 않은 테스트를 통과로 보고하지 않는다. 테스트를 삭제하거나 비활성화해서 통과시키지 않는다.
- 테스트는 `./gradlew test`(DB 불필요), `./gradlew dbTest`(Docker 필요), `./gradlew check`(전체)로 나뉜다. 완료 보고에는 실행한 명령을 적고, `test`만 실행했다면 DB 테스트는 미검증으로 보고한다.
- 작업을 마치면 컨벤션 15장의 체크리스트를 확인하고 15.2 형식으로 보고한다.
- 리팩토링 진행 상황이 바뀌면 `docs/refactoring_plan.md`를 갱신한다.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
