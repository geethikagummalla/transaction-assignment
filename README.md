# Transaction Processing API MVP

A clean, robust Spring Boot 3 REST API for processing and managing customer transactions backed by an in-memory H2 database.

---

## 1. Assumptions & Design Decisions

1. **State Machine & Lifecycle**:
   - New transactions always initialize with status `PENDING`.
   - Permitted transitions:
     - `PENDING` &rarr; `COMPLETED`
     - `PENDING` &rarr; `FAILED`
     - `PENDING` &rarr; `CANCELLED`
   - Terminal statuses (`COMPLETED`, `FAILED`, `CANCELLED`) are immutable and cannot transition to any other status.
2. **Idempotency & Uniqueness**:
   - `transactionId` is the primary key and must be globally unique across transactions. Attempting to create an existing `transactionId` yields `HTTP 409 Conflict`.
3. **Storage & Persistence**:
   - Uses embedded H2 database (`jdbc:h2:mem:transactions;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`) configured with JPA/Hibernate.
4. **Error Handling**:
   - Centralized `@RestControllerAdvice` translates domain exceptions and validation failures into standard structured error responses with appropriate HTTP status codes (`400`, `404`, `409`, `500`).

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
