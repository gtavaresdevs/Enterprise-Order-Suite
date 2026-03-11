-- Allow USER_UPDATED in identity_audit_events.type

ALTER TABLE identity_audit_events
DROP CONSTRAINT IF EXISTS identity_audit_events_type_check;

ALTER TABLE identity_audit_events
ADD CONSTRAINT identity_audit_events_type_check
CHECK (
    type IN (
        'USER_CREATED',
        'USER_DEACTIVATED',
        'USER_REACTIVATED',
        'USER_ROLE_CHANGED',
        'PASSWORD_SETUP_SENT',
        'USER_UPDATED'
    )
);