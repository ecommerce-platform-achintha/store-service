-- store-service baseline schema (docs/marketplace-design.md, sections 1, 4, 7 and 9).
-- Maintained from now on: never edit this file once applied, add V2__... instead.
-- UUID primary keys stay internal; every customer-facing entity also has a unique human-readable public_id.

-- ---------------------------------------------------------------------------------------------------------------
-- Stores. id = the merchant's storeId from the JWT (assigned by user-service at merchant registration).
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE stores (
    id               uuid          PRIMARY KEY,
    public_id        varchar(20)   NOT NULL,
    name             varchar(100)  NOT NULL,
    slug             varchar(120)  NOT NULL,
    description      varchar(2000),
    contact_phone    varchar(20),
    contact_email    varchar(254),
    address_line     varchar(300),
    city             varchar(100),
    district         varchar(50),
    logo_key         varchar(200),
    -- published: profile complete and the owner ACTIVE (stays true during BAN_GRACE, false once BANNED)
    published        boolean       NOT NULL DEFAULT false,
    -- accepting_orders: published and the owner not in BAN_GRACE. Public = published AND accepting_orders.
    accepting_orders boolean       NOT NULL DEFAULT false,
    owner_user_id    uuid          NOT NULL,
    -- Real owner status (incl. BAN_GRACE), from user-service; never shown to the merchant
    owner_status     varchar(32)   NOT NULL,
    cod_enabled      boolean       NOT NULL DEFAULT false,
    rating_avg       numeric(3,2)  NOT NULL DEFAULT 0,
    rating_count     integer       NOT NULL DEFAULT 0,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    version          bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ux_stores_public_id UNIQUE (public_id),
    CONSTRAINT ux_stores_slug UNIQUE (slug),
    CONSTRAINT ux_stores_owner UNIQUE (owner_user_id),
    CONSTRAINT ck_stores_rating CHECK (rating_avg >= 0 AND rating_avg <= 5 AND rating_count >= 0)
);
-- Store names are unique regardless of case
CREATE UNIQUE INDEX ux_stores_name_ci ON stores (lower(name));
CREATE INDEX idx_stores_visible ON stores (published, accepting_orders);

-- ---------------------------------------------------------------------------------------------------------------
-- Bank master (super admin) and store bank accounts (owner only, account number AES-GCM encrypted)
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE banks (
    code       varchar(20)  PRIMARY KEY,
    name       varchar(100) NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL
);

CREATE TABLE store_bank_accounts (
    id                 uuid          PRIMARY KEY,
    public_id          varchar(20)   NOT NULL,
    store_id           uuid          NOT NULL REFERENCES stores (id),
    bank_code          varchar(20)   NOT NULL REFERENCES banks (code),
    branch             varchar(100)  NOT NULL,
    account_name       varchar(150)  NOT NULL,
    -- "v1:" + base64(IV || ciphertext || tag); the plain number never reaches the database or the logs
    account_number_enc varchar(512)  NOT NULL,
    -- For masked reads (****1234) without decrypting
    account_last4      varchar(4)    NOT NULL,
    active             boolean       NOT NULL DEFAULT true,
    deleted_at         timestamptz,
    created_at         timestamptz   NOT NULL,
    updated_at         timestamptz   NOT NULL,
    version            bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ux_store_bank_accounts_public_id UNIQUE (public_id)
);
CREATE INDEX idx_store_bank_accounts_store ON store_bank_accounts (store_id) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------------------------------------------
-- Courier master (super admin), store couriers and shipping templates (owner only)
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE couriers (
    code                  varchar(30)  PRIMARY KEY,
    name                  varchar(100) NOT NULL,
    -- Contains the {trackingNumber} placeholder
    tracking_url_template varchar(500) NOT NULL,
    tracking_number_regex varchar(200) NOT NULL,
    cod_supported         boolean      NOT NULL DEFAULT false,
    active                boolean      NOT NULL DEFAULT true,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL
);

CREATE TABLE store_couriers (
    id           bigserial    PRIMARY KEY,
    store_id     uuid         NOT NULL REFERENCES stores (id),
    courier_code varchar(30)  NOT NULL REFERENCES couriers (code),
    enabled_at   timestamptz  NOT NULL,
    CONSTRAINT ux_store_couriers UNIQUE (store_id, courier_code)
);

CREATE TABLE shipping_templates (
    id             uuid          PRIMARY KEY,
    public_id      varchar(20)   NOT NULL,
    store_id       uuid          NOT NULL REFERENCES stores (id),
    courier_code   varchar(30)   NOT NULL REFERENCES couriers (code),
    name           varchar(100)  NOT NULL,
    rule_type      varchar(20)   NOT NULL,
    -- FLAT: the amount. PER_DISTRICT / WEIGHT_BAND: the fallback when no rate matches (optional).
    base_amount    numeric(12,2),
    -- PER_DISTRICT: [{"district","amount"}], WEIGHT_BAND: [{"maxWeightKg","amount"}] (JSON)
    rates          text          NOT NULL DEFAULT '[]',
    active         boolean       NOT NULL DEFAULT true,
    created_at     timestamptz   NOT NULL,
    updated_at     timestamptz   NOT NULL,
    version        bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ux_shipping_templates_public_id UNIQUE (public_id),
    CONSTRAINT ux_shipping_templates_store_courier UNIQUE (store_id, courier_code),
    CONSTRAINT ck_shipping_templates_rule CHECK (rule_type IN ('FLAT', 'PER_DISTRICT', 'WEIGHT_BAND')),
    CONSTRAINT ck_shipping_templates_amount CHECK (base_amount IS NULL OR base_amount >= 0)
);

-- ---------------------------------------------------------------------------------------------------------------
-- Platform settings (section 7) and their history, holidays
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE platform_settings (
    setting_key varchar(100) PRIMARY KEY,
    value       varchar(100) NOT NULL,
    value_type  varchar(16)  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    updated_by  varchar(40)  NOT NULL,
    version     bigint       NOT NULL DEFAULT 0,
    CONSTRAINT ck_platform_settings_type CHECK (value_type IN ('INTEGER', 'DECIMAL', 'BOOLEAN'))
);

CREATE TABLE platform_settings_history (
    id          bigserial    PRIMARY KEY,
    setting_key varchar(100) NOT NULL REFERENCES platform_settings (setting_key),
    old_value   varchar(100),
    new_value   varchar(100) NOT NULL,
    changed_by  varchar(40)  NOT NULL,
    changed_at  timestamptz  NOT NULL,
    reason      varchar(500)
);
CREATE INDEX idx_platform_settings_history_key ON platform_settings_history (setting_key, id);

-- Defaults exactly as contract section 7 (SettingKey holds the same values plus types and ranges)
INSERT INTO platform_settings (setting_key, value, value_type, updated_at, updated_by) VALUES
    ('timers.merchant-response-hours', '24', 'INTEGER', now(), 'SYSTEM'),
    ('timers.customer-confirmation-hours', '24', 'INTEGER', now(), 'SYSTEM'),
    ('timers.payment-submission-hours', '24', 'INTEGER', now(), 'SYSTEM'),
    ('timers.payment-verification-hours', '24', 'INTEGER', now(), 'SYSTEM'),
    ('timers.ship-by-hours', '72', 'INTEGER', now(), 'SYSTEM'),
    ('timers.auto-complete-days', '7', 'INTEGER', now(), 'SYSTEM'),
    ('timers.ban-grace-days', '14', 'INTEGER', now(), 'SYSTEM'),
    ('score.customer.completed', '1', 'INTEGER', now(), 'SYSTEM'),
    ('score.customer.declined', '-1', 'INTEGER', now(), 'SYSTEM'),
    ('score.customer.expired', '-1', 'INTEGER', now(), 'SYSTEM'),
    ('cod.objection-window-days', '7', 'INTEGER', now(), 'SYSTEM'),
    ('score.customer.cod-refusal-limit', '3', 'INTEGER', now(), 'SYSTEM'),
    ('score.customer.cod-suspension-months', '3', 'INTEGER', now(), 'SYSTEM'),
    ('score.merchant.completed', '1', 'INTEGER', now(), 'SYSTEM'),
    ('score.merchant.response-timeout', '-2', 'INTEGER', now(), 'SYSTEM'),
    ('score.merchant.late-shipment', '-1', 'INTEGER', now(), 'SYSTEM'),
    ('score.merchant.late-verification', '-1', 'INTEGER', now(), 'SYSTEM'),
    ('score.merchant.complaint-upheld', '-3', 'INTEGER', now(), 'SYSTEM'),
    ('score.merchant.at-risk-threshold', '-5', 'INTEGER', now(), 'SYSTEM'),
    ('orders.max-open-unconfirmed-per-customer', '10', 'INTEGER', now(), 'SYSTEM'),
    ('orders.max-quote-revisions', '2', 'INTEGER', now(), 'SYSTEM'),
    ('complaints.window-days', '30', 'INTEGER', now(), 'SYSTEM'),
    ('merchant.max-application-attempts', '3', 'INTEGER', now(), 'SYSTEM'),
    ('auth.access-token-minutes', '10', 'INTEGER', now(), 'SYSTEM'),
    ('auth.refresh-days', '14', 'INTEGER', now(), 'SYSTEM'),
    ('auth.max-failed-logins', '5', 'INTEGER', now(), 'SYSTEM'),
    ('auth.lockout-minutes', '15', 'INTEGER', now(), 'SYSTEM'),
    ('fees.monthly-lkr', '500.00', 'DECIMAL', now(), 'SYSTEM'),
    ('fees.per-order-lkr', '10.00', 'DECIMAL', now(), 'SYSTEM'),
    ('fees.enforced', 'false', 'BOOLEAN', now(), 'SYSTEM');

CREATE TABLE holidays (
    id           uuid         PRIMARY KEY,
    public_id    varchar(20)  NOT NULL,
    holiday_date date         NOT NULL,
    name         varchar(100) NOT NULL,
    holiday_type varchar(16)  NOT NULL,
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    CONSTRAINT ux_holidays_public_id UNIQUE (public_id),
    -- One entry per Colombo calendar day (a Poya that is also a public holiday is still one non-working day)
    CONSTRAINT ux_holidays_date UNIQUE (holiday_date),
    CONSTRAINT ck_holidays_type CHECK (holiday_type IN ('PUBLIC', 'POYA'))
);

-- ---------------------------------------------------------------------------------------------------------------
-- Merchant score and metrics (fed by order-events)
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE merchant_score_events (
    id              bigserial    PRIMARY KEY,
    store_id        uuid         NOT NULL,
    order_id        uuid,
    order_public_id varchar(20),
    event_type      varchar(32)  NOT NULL,
    delta           integer      NOT NULL,
    score_after     integer      NOT NULL,
    source_event_id uuid,
    created_at      timestamptz  NOT NULL
);
-- A penalty or bonus applies at most once per order, even if order-service sends it in two different events
CREATE UNIQUE INDEX ux_merchant_score_events_order ON merchant_score_events (store_id, order_id, event_type)
    WHERE order_id IS NOT NULL;
CREATE INDEX idx_merchant_score_events_store ON merchant_score_events (store_id, id);

CREATE TABLE merchant_metrics (
    store_id               uuid          PRIMARY KEY,
    score                  integer       NOT NULL DEFAULT 0,
    orders_received        integer       NOT NULL DEFAULT 0,
    orders_quoted          integer       NOT NULL DEFAULT 0,
    orders_rejected        integer       NOT NULL DEFAULT 0,
    response_count         integer       NOT NULL DEFAULT 0,
    total_response_seconds bigint        NOT NULL DEFAULT 0,
    cancelled_orders       integer       NOT NULL DEFAULT 0,
    late_shipments         integer       NOT NULL DEFAULT 0,
    late_verifications     integer       NOT NULL DEFAULT 0,
    complaints_upheld      integer       NOT NULL DEFAULT 0,
    completed_orders       integer       NOT NULL DEFAULT 0,
    revenue                numeric(14,2) NOT NULL DEFAULT 0,
    -- Set when the score falls below score.merchant.at-risk-threshold (admin alert), cleared when it recovers
    at_risk_since          timestamptz,
    updated_at             timestamptz   NOT NULL,
    version                bigint        NOT NULL DEFAULT 0
);
CREATE INDEX idx_merchant_metrics_score ON merchant_metrics (score);

-- ---------------------------------------------------------------------------------------------------------------
-- Store reviews, merchant replies, eligibility (from OrderCompleted)
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE review_eligibility (
    order_id        uuid         PRIMARY KEY,
    order_public_id varchar(20)  NOT NULL,
    store_id        uuid         NOT NULL,
    customer_id     uuid         NOT NULL,
    completed_at    timestamptz  NOT NULL,
    CONSTRAINT ux_review_eligibility_order_public_id UNIQUE (order_public_id)
);
CREATE INDEX idx_review_eligibility_customer ON review_eligibility (customer_id);

CREATE TABLE store_reviews (
    id              uuid          PRIMARY KEY,
    public_id       varchar(20)   NOT NULL,
    store_id        uuid          NOT NULL REFERENCES stores (id),
    order_id        uuid          NOT NULL REFERENCES review_eligibility (order_id),
    order_public_id varchar(20)   NOT NULL,
    customer_id     uuid          NOT NULL,
    rating          smallint      NOT NULL,
    body            varchar(2000),
    attachment_keys text          NOT NULL DEFAULT '[]',
    hidden          boolean       NOT NULL DEFAULT false,
    hidden_reason   varchar(500),
    hidden_by       varchar(40),
    hidden_at       timestamptz,
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    version         bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ux_store_reviews_public_id UNIQUE (public_id),
    -- One review per order
    CONSTRAINT ux_store_reviews_order UNIQUE (order_id),
    CONSTRAINT ck_store_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);
CREATE INDEX idx_store_reviews_store ON store_reviews (store_id, created_at) WHERE hidden = false;

CREATE TABLE review_replies (
    id               uuid          PRIMARY KEY,
    review_id        uuid          NOT NULL REFERENCES store_reviews (id),
    store_id         uuid          NOT NULL,
    body             varchar(2000) NOT NULL,
    author_user_id   uuid          NOT NULL,
    author_public_id varchar(40),
    author_role      varchar(32)   NOT NULL,
    created_at       timestamptz   NOT NULL,
    -- One merchant reply per review
    CONSTRAINT ux_review_replies_review UNIQUE (review_id)
);

-- ---------------------------------------------------------------------------------------------------------------
-- Fee ledger and credits (D11: recorded, never enforced or invoiced)
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE merchant_credits (
    id                   uuid          PRIMARY KEY,
    public_id            varchar(20)   NOT NULL,
    store_id             uuid          NOT NULL REFERENCES stores (id),
    amount               numeric(12,2) NOT NULL,
    reason               varchar(500)  NOT NULL,
    granted_by_user_id   uuid          NOT NULL,
    granted_by_public_id varchar(40),
    created_at           timestamptz   NOT NULL,
    CONSTRAINT ux_merchant_credits_public_id UNIQUE (public_id),
    CONSTRAINT ck_merchant_credits_amount CHECK (amount > 0)
);

CREATE TABLE fee_ledger (
    id              bigserial     PRIMARY KEY,
    store_id        uuid          NOT NULL,
    entry_type      varchar(20)   NOT NULL,
    -- Positive: owed by the merchant (fees). Negative: credits.
    amount          numeric(12,2) NOT NULL,
    order_id        uuid,
    order_public_id varchar(20),
    credit_id       uuid          REFERENCES merchant_credits (id),
    description     varchar(500),
    created_at      timestamptz   NOT NULL,
    CONSTRAINT ck_fee_ledger_type CHECK (entry_type IN ('ORDER_FEE', 'MONTHLY_FEE', 'CREDIT'))
);
-- One ORDER_FEE per order (consumer idempotency, also across different event ids)
CREATE UNIQUE INDEX ux_fee_ledger_order ON fee_ledger (order_id, entry_type) WHERE order_id IS NOT NULL;
CREATE INDEX idx_fee_ledger_store ON fee_ledger (store_id, id);

-- ---------------------------------------------------------------------------------------------------------------
-- Append-only audit log of privileged mutations (section 1). Sensitive fields are masked before insert.
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE audit_log (
    id              bigserial    PRIMARY KEY,
    occurred_at     timestamptz  NOT NULL,
    actor_id        uuid,
    actor_public_id varchar(40)  NOT NULL,
    actor_role      varchar(32)  NOT NULL,
    actor_store_id  uuid,
    action          varchar(64)  NOT NULL,
    target_type     varchar(32)  NOT NULL,
    target_id       varchar(64)  NOT NULL,
    before_state    text,
    after_state     text,
    reason          varchar(500)
);
CREATE INDEX idx_audit_log_target ON audit_log (target_type, target_id);
CREATE INDEX idx_audit_log_occurred_at ON audit_log (occurred_at);

CREATE FUNCTION audit_log_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_audit_log_append_only
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_append_only();

-- ---------------------------------------------------------------------------------------------------------------
-- Transactional outbox (section 9) and consumer de-duplication
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE outbox (
    id           bigserial     PRIMARY KEY,
    event_id     uuid          NOT NULL,
    aggregate_id uuid          NOT NULL,
    event_type   varchar(64)   NOT NULL,
    topic        varchar(100)  NOT NULL,
    msg_key      varchar(100)  NOT NULL,
    payload      text          NOT NULL,
    created_at   timestamptz   NOT NULL,
    published_at timestamptz,
    attempts     integer       NOT NULL DEFAULT 0,
    last_error   varchar(1000),
    CONSTRAINT ux_outbox_event_id UNIQUE (event_id)
);
CREATE INDEX idx_outbox_unpublished ON outbox (id) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    event_id     uuid         NOT NULL,
    consumer     varchar(100) NOT NULL,
    processed_at timestamptz  NOT NULL,
    PRIMARY KEY (event_id, consumer)
);

-- ---------------------------------------------------------------------------------------------------------------
-- ShedLock (JdbcTemplateLockProvider, usingDbTime)
-- ---------------------------------------------------------------------------------------------------------------
CREATE TABLE shedlock (
    name       varchar(64)  PRIMARY KEY,
    lock_until timestamptz  NOT NULL,
    locked_at  timestamptz  NOT NULL,
    locked_by  varchar(255) NOT NULL
);
