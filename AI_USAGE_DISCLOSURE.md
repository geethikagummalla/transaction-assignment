# AI Usage Disclosure

## 1. Overview & Statement of Transparency
In accordance with the submission guidelines and student checklist (*"Disclose AI assistance, if used"*), this document outlines how Artificial Intelligence (AI) tools were utilized in the development, refinement, and verification of the **Transaction Processing API **.

---

## 2. Tools Used
- **AI Coding Assistant / LLM**: Google Antigravity / Gemini-based AI assistant.
- **IDE / Environment**: Antigravity IDE on Windows.

---

## 3. Scope of AI Assistance

### A. Architectural Brainstorming & Design Validation
- **Finite State Machine (FSM) Design**: AI was consulted to validate best practices for modeling state transitions and terminal state immutability in Java enums versus using heavy external workflow engines.
- **Financial Precision Standards**: Discussed best practices for currency representations (`BigDecimal` with explicit precision and scale versus primitive `double`/`float`) to avoid IEEE 754 precision issues.

### B. Code Implementation & Scaffolding
- **Boilerplate Generation**: Scaffolding DTOs (`CreateTransactionRequest`, `UpdateTransactionStatusRequest`, `TransactionResponse`, `ErrorResponse`).
- **Input Validation Rules**: Formulated Jakarta Validation annotations (`@NotBlank`, `@Positive`, `@Digits`) paired with defense-in-depth domain validation rules in `TransactionService`.
- **Global Exception Handling**: Structured the `@RestControllerAdvice` class (`GlobalExceptionHandler`) to map domain exceptions into standardized RFC-7807 structured JSON responses.

### C. Test Suite Design & Edge Case Generation
- **Integration Test Scenarios**: Assisted in formulating comprehensive `MockMvc` test cases covering positive flows, negative boundary cases (e.g. negative amounts, $>2$ decimal places, invalid currencies, duplicate IDs), and illegal state transitions from terminal states.
- **Regression Checks**: Ensured backward compatibility with starter endpoints (`GET /api/sample`).

### D. Documentation & Interview Preparation
- **README & API Specifications**: Generated Markdown documentation, endpoint tables, and error schemas.
- **Interview Preparation Guide**: Formulated comprehensive architectural breakdowns, STAR interview pitch scripts, and live-coding scenario guides.

---

## 4. Human Oversight & Critical Verification

All code, architectural choices, and test outputs were critically reviewed, verified, and tested by the candidate:
1. **Domain Integrity**: Verified that the business rules match the exact problem specifications (e.g., initial state is strictly `PENDING`, terminal states are immutable).
2. **Local Compilation & Test Execution**: Ran all tests locally using `./mvnw.cmd clean test` to confirm `16/16` tests pass with zero failures or errors.
3. **Runtime Validation**: Executed the application live (`./mvnw.cmd spring-boot:run`), manually tested endpoints via HTTP requests, and validated H2 in-memory database interactions.
4. **Code Understanding**: Full comprehension of every class, method, annotation, and design trade-off, with complete readiness to explain, modify, or debug the application live in a technical interview setting.
