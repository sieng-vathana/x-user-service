-- Platform-wide backoffice access is separate from a merchant's OWNER role.
INSERT INTO permissions (permission_code, permission_name, module_name, description)
SELECT 'platform:admin', 'Platform administrator', 'PLATFORM',
       'Full access to platform backoffice operations'
WHERE NOT EXISTS (
    SELECT 1 FROM permissions WHERE permission_code = 'platform:admin'
);
