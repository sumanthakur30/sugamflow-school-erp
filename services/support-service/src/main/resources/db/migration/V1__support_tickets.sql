CREATE TABLE support_tickets (
    id BIGSERIAL PRIMARY KEY,
    ticket_number VARCHAR(30) NOT NULL UNIQUE,
    organization_id VARCHAR(100) NOT NULL,
    shop_id VARCHAR(100) NOT NULL,
    issue_type VARCHAR(40) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    contact_email VARCHAR(255),
    contact_mobile VARCHAR(20),
    module_name VARCHAR(100),
    app_version VARCHAR(50),
    device_info TEXT,
    submitted_by VARCHAR(255),
    assigned_to VARCHAR(255),
    sla_due_at TIMESTAMP,
    first_response_at TIMESTAMP,
    resolved_at TIMESTAMP,
    closed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_support_tickets_org_shop ON support_tickets (organization_id, shop_id);
CREATE INDEX idx_support_tickets_status ON support_tickets (status);
CREATE INDEX idx_support_tickets_priority ON support_tickets (priority);
CREATE INDEX idx_support_tickets_created_at ON support_tickets (created_at DESC);
CREATE INDEX idx_support_tickets_ticket_number ON support_tickets (ticket_number);

CREATE TABLE support_ticket_attachments (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES support_tickets (id) ON DELETE CASCADE,
    file_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    uploaded_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_support_ticket_attachments_ticket ON support_ticket_attachments (ticket_id);

CREATE TABLE support_ticket_comments (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES support_tickets (id) ON DELETE CASCADE,
    body TEXT NOT NULL,
    author VARCHAR(255) NOT NULL,
    staff_response BOOLEAN NOT NULL DEFAULT FALSE,
    internal_note BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_support_ticket_comments_ticket ON support_ticket_comments (ticket_id, created_at);

CREATE TABLE support_ticket_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    ticket_id BIGINT NOT NULL REFERENCES support_tickets (id) ON DELETE CASCADE,
    action VARCHAR(50) NOT NULL,
    field_name VARCHAR(50),
    old_value TEXT,
    new_value TEXT,
    actor VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_support_ticket_audit_ticket ON support_ticket_audit_logs (ticket_id, created_at);
