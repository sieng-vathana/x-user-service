-- Give every existing merchant a least-privilege role so owners can add staff immediately.
INSERT INTO roles (business_id, role_code, role_name, description, is_system)
SELECT DISTINCT owner.business_id,
       'CASHIER',
       'Cashier',
       'Can use point of sale, view products and sales, and manage customer records',
       1
FROM roles owner
WHERE owner.business_id IS NOT NULL
  AND UPPER(owner.role_code) = 'OWNER'
  AND NOT EXISTS (
      SELECT 1
      FROM roles existing
      WHERE existing.business_id = owner.business_id
        AND UPPER(existing.role_code) = 'CASHIER'
  );

-- Cashiers can work in POS, view products and sales, and create/find customers.
INSERT INTO role_permissions (role_id, permission_id)
SELECT cashier.id, permission.id
FROM roles cashier
JOIN permissions permission
  ON LOWER(permission.permission_code) IN (
      'x-bff:read',
      'x-customer:create',
      'x-customer:read',
      'x-order:create',
      'x-order:read',
      'x-product:read',
      'x-store:read'
  )
WHERE UPPER(cashier.role_code) = 'CASHIER'
  AND COALESCE(cashier.is_system, 0) = 1
  AND NOT EXISTS (
      SELECT 1
      FROM role_permissions existing
      WHERE existing.role_id = cashier.id
        AND existing.permission_id = permission.id
  );
