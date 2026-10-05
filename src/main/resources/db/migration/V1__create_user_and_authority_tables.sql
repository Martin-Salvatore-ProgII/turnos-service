-- Usuarios finales y sus autoridades. Los campos son los del usuario de JHipster
-- (ENUNCIADO §3.2), más el UUID que se envía a la cátedra como externalPatientId.

CREATE TABLE app_user (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_patient_id UUID         NOT NULL,
    login               VARCHAR(50)  NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    first_name          VARCHAR(50)  NOT NULL,
    last_name           VARCHAR(50)  NOT NULL,
    email               VARCHAR(254) NOT NULL,
    image_url           VARCHAR(256),
    activated           BOOLEAN      NOT NULL,
    lang_key            VARCHAR(10)  NOT NULL,
    created_by          VARCHAR(50)  NOT NULL,
    created_date        TIMESTAMPTZ  NOT NULL,
    last_modified_by    VARCHAR(50),
    last_modified_date  TIMESTAMPTZ,
    CONSTRAINT uq_app_user_external_patient_id UNIQUE (external_patient_id),
    CONSTRAINT uq_app_user_login UNIQUE (login),
    CONSTRAINT uq_app_user_email UNIQUE (email),
    -- login y email se guardan en minúsculas: el servicio los convierte antes de guardar
    -- y la base rechaza cualquier valor que llegue sin convertir.
    CONSTRAINT ck_app_user_login_lowercase CHECK (login = lower(login)),
    CONSTRAINT ck_app_user_email_lowercase CHECK (email = lower(email))
);

CREATE TABLE authority (
    name VARCHAR(50) PRIMARY KEY
);

CREATE TABLE app_user_authority (
    user_id        BIGINT      NOT NULL REFERENCES app_user (id),
    authority_name VARCHAR(50) NOT NULL REFERENCES authority (name),
    PRIMARY KEY (user_id, authority_name)
);

INSERT INTO authority (name) VALUES ('ROLE_USER'), ('ROLE_ADMIN');
