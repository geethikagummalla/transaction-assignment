# Transaction Processing API

A clean, robust Spring Boot 3 REST API for processing and managing customer transactions backed by an in-memory H2 database.

---

## 1. Architectural & Design Decisions

The design of this application deliberately resolves seven key architectural questions:

### 1.1 Validation Rules & Business Invariants
* **Decisions Made**:
  * `transactionId`: Mandatory, non-blank string, unique primary key. Explicitly restricted from matching `customerId` (sanity check: an entity ID must not collide with its customer identifier).
  * `customerId`: Mandatory, non-blank string.
  * `amount`: Mandatory, strictly positive (`> 0.00`), max limit capped at `1,000,000.00` (an MVP anti-money laundering / fraud threshold), and strictly capped at 2 decimal places. Evaluated using `BigDecimal.scale()` and `stripTrailingZeros().scale()`.
  * `currency`: Whitelisted to 6 major ISO 4217 currencies (`USD`, `EUR`, `GBP`, `CAD`, `JPY`, `INR`). Input is case-insensitive and automatically normalized to uppercase.
  * `transactionType`: Whitelisted to 5 operations (`PAYMENT`, `REFUND`, `TRANSFER`, `DEPOSIT`, `WITHDRAWAL`). Normalized to uppercase.
  * `status`: Optional in creation payload; if provided, it must strictly be `PENDING`. Transactions cannot be born directly into terminal or completed states.
* **Rationale & Why**:
  * *Multi-Tier Defense*: Jakarta Bean Validation annotations (`@NotBlank`, `@NotNull`, `@Positive`, `@Digits`) catch malformed inputs early at the controller boundary. Programmatic validation in `TransactionService` acts as a secondary layer ensuring domain invariants are never bypassed.
  * *Financial Accuracy*: Avoided `float` and `double` because IEEE 754 binary floating-point representation causes cumulative rounding errors (e.g. `0.1 + 0.2 = 0.30000000000000004`). `BigDecimal` guarantees exact base-10 arithmetic.

### 1.2 Project & Class Structure (Layered Architecture)
* **Decisions Made**:
  * Adopted a clean **Package-by-Layer** structure separating presentation, business logic, persistence, and contracts:
    * `controller`: Thin REST controllers (`TransactionController`, `SampleController`) responsible solely for HTTP routing, request binding, status codes, and delegating to services.
    * `service`: Business layer (`TransactionService`) encapsulating all business logic, validation rules, state machine orchestration, and transactional boundaries (`@Transactional`).
    * `repository`: Data access interfaces (`TransactionRepository`) extending Spring Data JPA `JpaRepository`.
    * `model`: JPA entities and core domain models (`Transaction`, `TransactionStatus`).
    * `dto`: Dedicated Data Transfer Objects (`CreateTransactionRequest`, `UpdateTransactionStatusRequest`, `TransactionResponse`, `ErrorResponse`).
    * `exception`: Domain-specific exceptions and centralized `@RestControllerAdvice` (`GlobalExceptionHandler`).
* **Rationale & Why**:
  * *Decoupling & Security*: Exposing `@Entity` models directly in API requests/responses causes leaky abstractions, exposes internal database column mappings, and creates vulnerability to over-posting / mass assignment attacks. DTOs decouple external API contracts from the underlying database schema.

### 1.3 API Endpoint Design & REST Semantics
* **Decisions Made**:
  * Follows RESTful resource-oriented conventions under `/api/transactions`:
    * `POST /api/transactions`: Creates a transaction, returns `201 Created` with created resource payload.
    * `GET /api/transactions/{transactionId}`: Fetches transaction by ID, returns `200 OK` or `404 Not Found`.
    * `PATCH /api/transactions/{transactionId}/status` & `PUT /api/transactions/{transactionId}/status`: Updates status. Supported both `PATCH` (semantically standard for partial resource modification) and `PUT` (for clients favoring idempotent status replacement), returning `200 OK`.
    * `GET /api/transactions?customerId={customerId}` & `GET /api/transactions/customer/{customerId}`: Supports both standard query filtering and dedicated sub-resource routing for customer transaction history.
    * `GET /api/sample`: Preserved for backward compatibility with the starter project.
* **Rationale & Why**:
  * Adheres to REST best practices (Richardson Maturity Model Level 2) and HTTP specifications. Explicit status codes (`201`, `200`, `400`, `404`, `409`) make the API intuitive and predictable for frontend clients and microservice integrations.

### 1.4 Error Handling & Global Exception Management
* **Decisions Made**:
  * Centralized exception interception using `@RestControllerAdvice` (`GlobalExceptionHandler`).
  * Structured, consistent error payload schema inspired by RFC-7807 (Problem Details):
    * `timestamp`: ISO-8601 UTC timestamp.
    * `status`: HTTP status code integer (`400`, `404`, `409`, `500`).
    * `error`: Standard HTTP error reason phrase.
    * `message`: Clear, human-readable description of what failed.
    * `path`: Request URI that triggered the failure.
  * Custom exception mapping:
    * `DuplicateTransactionException` $\rightarrow$ `HTTP 409 Conflict`.
    * `TransactionNotFoundException` $\rightarrow$ `HTTP 404 Not Found`.
    * `InvalidTransactionException` & `InvalidStatusTransitionException` $\rightarrow$ `HTTP 400 Bad Request`.
    * `MethodArgumentNotValidException` (Bean validation) $\rightarrow$ `HTTP 400 Bad Request` with joined field-level messages.
    * Generic `Exception` $\rightarrow$ `HTTP 500 Internal Server Error`.
* **Rationale & Why**:
  * Prevents internal database stack traces or driver details from leaking to clients (security best practice), while ensuring client applications receive predictable, parseable error responses.

### 1.5 Entity & Domain Model Design (Finite State Machine)
* **Decisions Made**:
  * `Transaction` entity:
    * `transactionId`: Defined as a natural String primary key (`@Id`) rather than an auto-incrementing database sequence. This allows client applications to provide idempotency keys to safely retry requests without double-billing.
    * `amount`: Stored as `BigDecimal` mapped with `@Column(nullable = false, precision = 15, scale = 2)`.
  * `TransactionStatus` (Encapsulated State Machine):
    * Modeled as a domain enum encapsulating lifecycle rules: `isTerminal()` and `canTransitionTo(TransactionStatus target)`.
    * Permitted transitions: `PENDING` &rarr; `COMPLETED`, `FAILED`, `CANCELLED`.
    * Terminal state immutability: `COMPLETED`, `FAILED`, and `CANCELLED` cannot transition to any other status.
* **Rationale & Why**:
  * *Rich Domain Model*: State transition rules belong to the domain model. Embedding transition logic in the enum guarantees that state invariants remain consistent and self-contained rather than scattered across services.

### 1.6 Service & Repository Structure
* **Decisions Made**:
  * `TransactionService`:
    * Service layer acts as the single source of truth for business operations.
    * Method-level transactional boundaries using `@Transactional`.
    * Read operations use `@Transactional(readOnly = true)` to disable Hibernate dirty-checking and optimize database connection usage.
    * Performs orchestration: DTO validation, idempotency checks (`existsById`), entity transformation, state machine transition checks, and persistence.
  * `TransactionRepository`:
    * Extends Spring Data JPA `JpaRepository<Transaction, String>`.
    * Leverages derived query method: `List<Transaction> findByCustomerId(String customerId)`.
    * Uses built-in `existsById(id)` for fast primary-key index presence checks.
* **Rationale & Why**:
  * Complete separation of persistence from business logic. Services own business rules and transactional guarantees, keeping repositories simple and testable.

### 1.7 Testing Strategy & Extra Edge Case Tests
* **Decisions Made**:
  * Went beyond the minimum requirement ("at least four meaningful tests") to build a **16-test comprehensive MockMvc integration test suite**.
  * The test suite deliberately tests happy paths alongside critical boundary conditions and security hazards:
    1. **Happy Path Creation**: Verifies 201 Created and default `PENDING` status.
    2. **Field Validation**: Rejection of blank required fields (`400 Bad Request`).
    3. **Amount Boundaries**: Rejection of zero and negative amounts (`400 Bad Request`).
    4. **Decimal Precision**: Rejection of amounts with $>2$ decimal places like `100.555` (`400 Bad Request`).
    5. **Currency Whitelist**: Rejection of unsupported currency strings (`400 Bad Request`).
    6. **Initial Status Integrity**: Rejection of direct creation in non-PENDING status (`400 Bad Request`).
    7. **Idempotency & Duplicate Key**: Rejection of duplicate `transactionId` with `409 Conflict`.
    8. **ID Lookup**: Successful retrieval by primary key returning complete fields (`200 OK`).
    9. **404 Handling**: Non-existent transaction lookups return `404 Not Found`.
    10. **FSM Happy Path 1**: Status transition `PENDING` &rarr; `COMPLETED` (`200 OK`).
    11. **FSM Happy Path 2**: Status transitions `PENDING` &rarr; `FAILED` and `CANCELLED` (`200 OK`).
    12. **FSM Invariance / Terminal State**: Attempting to alter a terminal status (`COMPLETED` &rarr; `CANCELLED`) is rejected with `400 Bad Request`.
    13. **Status Update 404**: Attempting to update a non-existent transaction returns `404 Not Found`.
    14. **Customer Lookup**: Filtering transactions by customer ID via query param and path variable.
    15. **Starter Endpoint Regression**: Ensures `GET /api/sample` remains functional.
    16. **Context Load**: Validates Spring application context bootstrap.
* **Rationale & Why**:
  * In financial processing, edge cases (fractional cents, duplicate keys, illegal state rewrites) are the primary source of critical production defects. Testing these boundaries with full-stack `MockMvc` tests ensures serialization, validation, HTTP status codes, and database persistence operate seamlessly together.

---

## 2. Validation Rules

| Field | Type | Validation Rules |
| :--- | :--- | :--- |
| `transactionId` | String | Required (non-blank), unique primary key. Must not equal `customerId`. |
| `customerId` | String | Required (non-blank). |
| `amount` | BigDecimal | Required (non-null), positive (`> 0.00`), max 2 decimal places, max limit `1,000,000.00`. |
| `currency` | String | Required (non-blank). Allowed: `USD`, `EUR`, `GBP`, `CAD`, `JPY`, `INR` (case-insensitive). |
| `transactionType` | String | Required (non-blank). Allowed: `PAYMENT`, `REFUND`, `TRANSFER`, `DEPOSIT`, `WITHDRAWAL`. |
| `status` | String | Initialized as `PENDING`. If supplied in creation request, must be `PENDING` (otherwise `400 Bad Request`). |

---

## 3. API Reference

### Base URL: `/api/transactions`

#### 1. Create Transaction
- **Method**: `POST /api/transactions`
- **Request Body**:
```json
{
  "transactionId": "TXN-1001",
  "customerId": "CUST-501",
  "amount": 150.75,
  "currency": "USD",
  "transactionType": "PAYMENT"
}
```
- **Response**: `201 Created`
```json
{
  "transactionId": "TXN-1001",
  "customerId": "CUST-501",
  "amount": 150.75,
  "currency": "USD",
  "transactionType": "PAYMENT",
  "status": "PENDING"
}
```

---

#### 2. Get Transaction by ID
- **Method**: `GET /api/transactions/{transactionId}`
- **Response**: `200 OK`
```json
{
  "transactionId": "TXN-1001",
  "customerId": "CUST-501",
  "amount": 150.75,
  "currency": "USD",
  "transactionType": "PAYMENT",
  "status": "PENDING"
}
```
- **Error**: `404 Not Found` if the transaction does not exist.

---

#### 3. Update Transaction Status
- **Method**: `PATCH /api/transactions/{transactionId}/status` (or `PUT /api/transactions/{transactionId}/status`)
- **Request Body**:
```json
{
  "status": "COMPLETED"
}
```
- **Response**: `200 OK`
```json
{
  "transactionId": "TXN-1001",
  "customerId": "CUST-501",
  "amount": 150.75,
  "currency": "USD",
  "transactionType": "PAYMENT",
  "status": "COMPLETED"
}
```
- **Error Codes**:
  - `400 Bad Request`: Invalid transition (e.g. updating a terminal status) or invalid status value.
  - `404 Not Found`: Transaction does not exist.

---

#### 4. Get Transactions by Customer ID
- **Method**: `GET /api/transactions?customerId={customerId}` or `GET /api/transactions/customer/{customerId}`
- **Response**: `200 OK`
```json
[
  {
    "transactionId": "TXN-1001",
    "customerId": "CUST-501",
    "amount": 150.75,
    "currency": "USD",
    "transactionType": "PAYMENT",
    "status": "COMPLETED"
  }
]
```

---

## 4. Testing & Verification

### Running the Test Suite

#### Windows
```cmd
.\mvnw.cmd clean test
```

#### Linux / macOS
```bash
./mvnw clean test
```

### Test Coverage Highlights
- **Successful Creation**: Validates 201 Created and default `PENDING` status.
- **Validation Rejections**:
  - Blank required fields (`400 Bad Request`)
  - Negative/zero amounts and amounts with >2 decimal places (`400 Bad Request`)
  - Unsupported currencies and transaction types (`400 Bad Request`)
  - Non-pending initial creation status (`400 Bad Request`)
- **Conflict Handling**: Duplicate transaction IDs return `409 Conflict`.
- **404 Handling**: Non-existent transaction lookups and updates return `404 Not Found`.
- **Status Transitions**:
  - Valid transitions: `PENDING` &rarr; `COMPLETED`, `FAILED`, `CANCELLED` (`200 OK`)
  - Invalid transitions: Attempting to modify terminal status `COMPLETED` &rarr; `CANCELLED` (`400 Bad Request`)
- **Customer Lookup**: Verifies filtering transactions by customer ID.
- **Backward Compatibility**: Verifies starter endpoint `GET /api/sample` remains functional.

---

## 5. Limitations & Future Improvements

1. **Pagination & Sorting**: Currently customer transaction lookups return the full list. Adding Spring Data `Pageable` support (`page`, `size`, `sort`) would optimize large transaction histories.
2. **Audit Logging & Timestamping**: Add `createdAt` and `updatedAt` audit columns (`@CreatedDate`, `@LastModifiedDate`).
3. **Distributed Locking / Optimistic Locking**: Add `@Version` field to `Transaction` entity to handle concurrent status update race conditions in high-throughput environments.
4. **Persistent RDBMS**: Swap in PostgreSQL or MySQL profile for production environments with migration tools like Flyway / Liquibase.
5. **API Documentation**: Add OpenAPI / Swagger UI contract specification for external consumers.

---

## 6. Test Run Output

All 16 tests executed via `./mvnw clean test` (or `.\mvnw.cmd clean test`) pass with `0` failures and `0` errors. The full console output is also preserved in [TEST_RUN_OUTPUT.txt](TEST_RUN_OUTPUT.txt).

```
-------------------------------------------------------
 T E S T S
-------------------------------------------------------
Running com.example.transactionstarter.controller.TransactionControllerTest
Started TransactionControllerTest in 4.472 seconds
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 6.910 s -- in com.example.transactionstarter.controller.TransactionControllerTest

Running com.example.transactionstarter.TransactionStarterApplicationTests
Started TransactionStarterApplicationTests in 0.471 seconds
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.481 s -- in com.example.transactionstarter.TransactionStarterApplicationTests

Results:

Tests run: 16, Failures: 0, Errors: 0, Skipped: 0

------------------------------------------------------------------------
BUILD SUCCESS
------------------------------------------------------------------------
Total time:  11.911 s
Finished at: 2026-09-02T11:52:56+05:30
------------------------------------------------------------------------
```

---

## 7. AI Usage Disclosure

In compliance with the candidate submission guidelines, AI assistance (Google Antigravity / Gemini) was utilized during the development of this project. For full transparency, see [AI_USAGE_DISCLOSURE.md](AI_USAGE_DISCLOSURE.md).

- **Architectural & Design Brainstorming**: Consulted AI to evaluate Finite State Machine (FSM) representation inside enum vs external workflow engines, and verified `BigDecimal` precision practices.
- **Code & Test Scaffolding**: Assisted in generating repetitive boilerplate (DTOs, error mappings) and formulating comprehensive `MockMvc` edge case test cases.
- **Human Verification & Ownership**: All code, business rules, and tests were critically inspected, refined, compiled, and verified locally by the candidate. The candidate holds full comprehension of every class, method, and architectural decision.

