CREATE TABLE properties (
    id BIGINT PRIMARY KEY,
    supplier_id VARCHAR(255) NOT NULL,
    supplier_property_code VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_properties_supplier_code UNIQUE (supplier_id, supplier_property_code)
);

CREATE TABLE room_types (
    id BIGINT PRIMARY KEY,
    property_id BIGINT NOT NULL,
    supplier_room_type_code VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    max_occupancy INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_room_types_property_code UNIQUE (property_id, supplier_room_type_code),
    CONSTRAINT fk_room_types_property FOREIGN KEY (property_id) REFERENCES properties(id)
);

CREATE INDEX idx_properties_supplier_status ON properties (supplier_id, status);
CREATE INDEX idx_room_types_property_status ON room_types (property_id, status);
