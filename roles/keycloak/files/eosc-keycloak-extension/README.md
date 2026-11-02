# EOSC Keycloak extension (Keycloak 26)

Port of `keycloak-flow-extension` 1.0.0 (Keycloak 4.5) to Keycloak 26.8.1.
Apache License 2.0, as the original.

One authenticator, **Create EOSC User If Unique**
(`eosc-idp-create-user-if-unique`), used in the realm's
"EOSC First Broker Login" flow for the Google and LS AAI identity providers.
On a user's first IdP login it

1. derives the username from the e-mail (`name@example.org` -> `name_example`),
2. adds the user to LDAP (`posixAccount`, home `/home/<first 16 hex of
   sha256(username)>`, `uidNumber` = highest + 1, random password) unless the
   e-mail is there already,
3. syncs the realm's LDAP provider, then creates or finds the Keycloak user.

`provision_account.py` on the login node then makes the home directory and
the Slurm account for the new LDAP user.

Changes from 1.0.0: Jakarta EE and the Keycloak 26 storage/user APIs; one LDAP
connection per registration, closed afterwards (1.0.0 kept one connection for
ever, so registrations failed once LDAP had dropped it). The "Link IDP Login"
authenticator is gone: Keycloak's own "Automatically set existing user"
(`idp-auto-link`) does the same.

The authenticator's LDAP settings (URL, bind DN and password, user base DN,
default gid, first uid) are its config in the flow, not in this code.

Build: `mvn package` (JDK 21) gives `target/eosc-keycloak-extension.jar`; the
keycloak role builds it inside the image (`files/Dockerfile`).
