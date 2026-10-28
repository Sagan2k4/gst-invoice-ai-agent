CREATE TABLE businesses (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    gstin       VARCHAR(15),
    state_code  VARCHAR(2),
    created_at  TIMESTAMP(6) NOT NULL
);

CREATE TABLE users (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id    BIGINT       NOT NULL,
    full_name      VARCHAR(120) NOT NULL,
    email          VARCHAR(180) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_business FOREIGN KEY (business_id) REFERENCES businesses (id)
);

CREATE TABLE invoices (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    business_id        BIGINT        NOT NULL,
    uploaded_by        BIGINT        NOT NULL,
    file_name          VARCHAR(255)  NOT NULL,
    file_path          VARCHAR(500)  NOT NULL,
    content_type       VARCHAR(100)  NOT NULL,
    file_hash          VARCHAR(64)   NOT NULL,
    status             VARCHAR(20)   NOT NULL,
    supplier_name      VARCHAR(255),
    supplier_gstin     VARCHAR(15),
    buyer_name         VARCHAR(255),
    buyer_gstin        VARCHAR(15),
    invoice_number     VARCHAR(64),
    invoice_date       DATE,
    place_of_supply    VARCHAR(2),
    supply_type        VARCHAR(10),
    taxable_value      DECIMAL(15, 2),
    cgst               DECIMAL(15, 2),
    sgst               DECIMAL(15, 2),
    igst               DECIMAL(15, 2),
    cess               DECIMAL(15, 2),
    total_amount       DECIMAL(15, 2),
    category           VARCHAR(40),
    ai_confidence      DECIMAL(4, 3),
    duplicate_of_id    BIGINT,
    validation_issues  TEXT,
    ai_notes           VARCHAR(1000),
    error_message      VARCHAR(1000),
    created_at         TIMESTAMP(6)  NOT NULL,
    updated_at         TIMESTAMP(6)  NOT NULL,
    CONSTRAINT fk_invoices_business FOREIGN KEY (business_id) REFERENCES businesses (id),
    CONSTRAINT fk_invoices_user FOREIGN KEY (uploaded_by) REFERENCES users (id)
);

CREATE INDEX idx_invoices_business_date ON invoices (business_id, invoice_date);
CREATE INDEX idx_invoices_business_hash ON invoices (business_id, file_hash);
CREATE INDEX idx_invoices_business_supplier_no ON invoices (business_id, supplier_gstin, invoice_number);

CREATE TABLE invoice_line_items (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    invoice_id     BIGINT        NOT NULL,
    line_no        INT           NOT NULL,
    description    VARCHAR(500),
    hsn_sac        VARCHAR(8),
    quantity       DECIMAL(15, 3),
    unit_price     DECIMAL(15, 2),
    taxable_value  DECIMAL(15, 2),
    gst_rate       DECIMAL(5, 2),
    cgst           DECIMAL(15, 2),
    sgst           DECIMAL(15, 2),
    igst           DECIMAL(15, 2),
    CONSTRAINT fk_line_items_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE
);
