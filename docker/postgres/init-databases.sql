-- Una base de datos y un usuario por bounded context (ADR-001 / PA-05): ningún microservicio puede leer ni escribir
-- las tablas de otro, por lo que no existen FKs cruzadas. Contraseñas SOLO para el entorno local de desarrollo/QA;
-- en la nube se inyectan desde el gestor de secretos.
CREATE USER identity_user WITH PASSWORD 'identity_local_dev';
CREATE USER property_user WITH PASSWORD 'property_local_dev';
CREATE USER pricing_user WITH PASSWORD 'pricing_local_dev';
CREATE USER quotation_user WITH PASSWORD 'quotation_local_dev';
CREATE USER communication_user WITH PASSWORD 'communication_local_dev';

CREATE DATABASE identity_db OWNER identity_user;
CREATE DATABASE property_db OWNER property_user;
CREATE DATABASE pricing_db OWNER pricing_user;
CREATE DATABASE quotation_db OWNER quotation_user;
CREATE DATABASE communication_db OWNER communication_user;

REVOKE ALL ON DATABASE identity_db FROM PUBLIC;
REVOKE ALL ON DATABASE property_db FROM PUBLIC;
REVOKE ALL ON DATABASE pricing_db FROM PUBLIC;
REVOKE ALL ON DATABASE quotation_db FROM PUBLIC;
REVOKE ALL ON DATABASE communication_db FROM PUBLIC;

GRANT CONNECT ON DATABASE identity_db TO identity_user;
GRANT CONNECT ON DATABASE property_db TO property_user;
GRANT CONNECT ON DATABASE pricing_db TO pricing_user;
GRANT CONNECT ON DATABASE quotation_db TO quotation_user;
GRANT CONNECT ON DATABASE communication_db TO communication_user;
