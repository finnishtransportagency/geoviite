create type integrations.ratko_push_status as enum ('IN_PROGRESS', 'SUCCESSFUL', 'FAILED', 'CONNECTION_ISSUE');
create type integrations.ratko_push_error_type as enum ('PROPERTIES', 'LOCATION', 'GEOMETRY');
create type integrations.ratko_push_error_operation as enum ('CREATE', 'UPDATE', 'DELETE');
