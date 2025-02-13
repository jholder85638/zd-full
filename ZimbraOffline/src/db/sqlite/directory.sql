--
-- 
--

-- -----------------------------------------------------------------------
-- directory
-- -----------------------------------------------------------------------

CREATE TABLE directory (
   entry_id    INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
   entry_type  CHAR(4) NOT NULL,
   entry_name  VARCHAR(128) NOT NULL,
   zimbra_id   CHAR(36) UNIQUE,
   modified    SMALLINT NOT NULL,

   UNIQUE(entry_type, entry_name)
);

CREATE UNIQUE INDEX ui_directory_zimbra_id ON directory(zimbra_id);

CREATE TABLE directory_attrs (
   entry_id    INTEGER NOT NULL,
   name        VARCHAR(255) NOT NULL,
   value       VARCHAR(10240) NOT NULL,

   CONSTRAINT fk_dattr_entry_id FOREIGN KEY (entry_id) REFERENCES directory(entry_id) ON DELETE CASCADE
);

CREATE INDEX i_dattr_entry_id_name ON directory_attrs(entry_id, name);
CREATE INDEX i_dattr_name ON directory_attrs(name);

CREATE TABLE directory_leaf (
   entry_id    INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
   parent_id   INTEGER NOT NULL,
   entry_type  CHAR(4) NOT NULL,
   entry_name  VARCHAR(128) NOT NULL,
   zimbra_id   CHAR(36) NOT NULL UNIQUE,

   UNIQUE (parent_id, entry_type, entry_name),
   CONSTRAINT fk_dleaf_entry_id FOREIGN KEY (parent_id) REFERENCES directory(entry_id) ON DELETE CASCADE
);


CREATE TABLE directory_leaf_attrs (
   entry_id    INTEGER NOT NULL,
   name        VARCHAR(255) NOT NULL,
   value       VARCHAR(10240) NOT NULL,

   CONSTRAINT fk_dleafattr_entry_id FOREIGN KEY (entry_id) REFERENCES directory_leaf(entry_id) ON DELETE CASCADE
);

CREATE INDEX i_dleafattr_entry_id_name ON directory_leaf_attrs(entry_id, name);
CREATE INDEX i_dleafattr_name ON directory_leaf_attrs(name);

CREATE TABLE directory_granter (
   granter_name  VARCHAR(128) NOT NULL,
   granter_id    CHAR(36) NOT NULL,
   grantee_id    CHAR(36) NOT NULL,

   PRIMARY KEY (granter_name, grantee_id)
);

CREATE INDEX i_dgranter_gter_name ON directory_granter(granter_name);
CREATE INDEX i_dgranter_gter_id ON directory_granter(granter_id);
CREATE INDEX i_dgranter_gtee_id ON directory_granter(grantee_id);

