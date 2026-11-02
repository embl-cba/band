#!/usr/bin/env python3
"""Adapt the realm export of the old Keycloak (4.5) for the new one (26).

    prepare-realm.py <export.json> <import.json>

- every identity provider (google, lsaai) uses "EOSC First Broker Login",
  so LS AAI users also get an LDAP account;
- lsaai moves from LS AAI's old CAS endpoints (which no longer know the
  client) to its current OIDC ones, with signature checks;
- in that flow, the dropped "Link IDP Login" step becomes Keycloak's own
  "Automatically set existing user" (idp-auto-link);
- the client "groupmanager" (its server is gone), its roles, its
  service-account user and the admin permissions on it are removed;
- JavaScript authorization policies (gone since Keycloak 18; the old default
  one granted everything) and permissions applying only them are removed;
- the portal client's tokens get "resource" and "eosc-client" in their
  audience: Keycloak 26 only lets a client introspect tokens it is an
  audience of (eosc-rs introspects as "resource"), and eosc-rs then wants its
  resource id "eosc-client" in aud (Keycloak 4.5 set aud to the client).

Prints only what it changed; the files hold users, keys and secrets.
"""
import json
import sys

FLOW = "EOSC First Broker Login"
OLD_LINK, NEW_LINK = "link-idp-login-authenticator", "idp-auto-link"
DROP_CLIENT = "groupmanager"
PORTAL_CLIENT, INTROSPECTOR = "eosc-client", "resource"
LSAAI = "https://login.aai.lifescience-ri.eu/oidc"
LSAAI_CONFIG = {"issuer": LSAAI + "/", "authorizationUrl": LSAAI + "/authorize",
                "tokenUrl": LSAAI + "/token", "userInfoUrl": LSAAI + "/userinfo",
                "jwksUrl": LSAAI + "/jwk", "logoutUrl": LSAAI + "/endsession",
                "useJwksUrl": "true", "validateSignature": "true"}

src, dst = sys.argv[1], sys.argv[2]
with open(src) as f:
    realm = json.load(f)
if isinstance(realm, list):          # singleFile export of one realm
    (realm,) = realm

if not any(fl["alias"] == FLOW for fl in realm.get("authenticationFlows", [])):
    sys.exit(f"flow '{FLOW}' not in the export")

for idp in realm.get("identityProviders", []):
    if idp.get("firstBrokerLoginFlowAlias") != FLOW:
        print(f"identity provider {idp['alias']}: first broker login flow "
              f"'{idp.get('firstBrokerLoginFlowAlias')}' -> '{FLOW}'")
        idp["firstBrokerLoginFlowAlias"] = FLOW

for idp in realm.get("identityProviders", []):
    if idp["alias"] == "lsaai" and "/cas/" in idp["config"].get("tokenUrl", ""):
        idp["config"].update(LSAAI_CONFIG)
        print(f"identity provider lsaai: endpoints -> {LSAAI}/...")

for fl in realm.get("authenticationFlows", []):
    for ex in fl.get("authenticationExecutions", []):
        if ex.get("authenticator") == OLD_LINK:
            print(f"flow '{fl['alias']}': {OLD_LINK} -> {NEW_LINK}")
            ex["authenticator"] = NEW_LINK

clients = realm.get("clients", [])
gone = [c for c in clients if c["clientId"] == DROP_CLIENT]

# JavaScript policies were removed in Keycloak 18; the old default one only
# granted everything. Drop them and the permissions that apply only them.
for c in clients:
    az = c.get("authorizationSettings")
    if not az:
        continue
    js = {p["name"] for p in az.get("policies", []) if p.get("type") == "js"}
    if not js:
        continue
    def only_js(p):
        applied = json.loads(p.get("config", {}).get("applyPolicies", "[]"))
        return applied and set(applied) <= js
    dropped = [p["name"] for p in az["policies"] if p["name"] in js or only_js(p)]
    az["policies"] = [p for p in az["policies"] if p["name"] not in dropped]
    print(f"client {c['clientId']}: authorization policies {dropped} removed (js)")

# realm-management's admin permissions on the removed client go with it
for g in gone:
    res = f"client.resource.{g['id']}"
    for c in clients:
        az = c.get("authorizationSettings")
        if not az:
            continue
        before = len(az.get("policies", [])) + len(az.get("resources", []))
        az["resources"] = [r for r in az.get("resources", []) if r.get("name") != res]
        az["policies"] = [p for p in az.get("policies", [])
                          if res not in json.loads(p.get("config", {}).get("resources", "[]"))]
        n_gone = before - len(az["policies"]) - len(az["resources"])
        if n_gone:
            print(f"client {c['clientId']}: {n_gone} admin permissions/resources for {DROP_CLIENT} removed")
if gone:
    realm["clients"] = [c for c in clients if c["clientId"] != DROP_CLIENT]
    realm.get("roles", {}).get("client", {}).pop(DROP_CLIENT, None)
    n = 0
    for u in realm.get("users", []):
        if u.get("clientRoles", {}).pop(DROP_CLIENT, None) is not None:
            n += 1
    realm.get("clientScopeMappings", {}).pop(DROP_CLIENT, None)
    # its service-account user points at the client: goes with it
    sa = [u for u in realm.get("users", []) if u.get("serviceAccountClientId") == DROP_CLIENT]
    realm["users"] = [u for u in realm.get("users", []) if u.get("serviceAccountClientId") != DROP_CLIENT]
    print(f"client {DROP_CLIENT} removed (its roles taken from {n} users, "
          f"{len(sa)} service-account user {[u['username'] for u in sa]} removed)")

for c in realm.get("clients", []):
    if c["clientId"] == PORTAL_CLIENT:
        ms = c.setdefault("protocolMappers", [])
        for aud in (INTROSPECTOR, PORTAL_CLIENT):
            if not any(m.get("protocolMapper") == "oidc-audience-mapper"
                       and m.get("config", {}).get("included.client.audience") == aud for m in ms):
                ms.append({"name": f"{aud}-audience", "protocol": "openid-connect",
                           "protocolMapper": "oidc-audience-mapper", "consentRequired": False,
                           "config": {"included.client.audience": aud,
                                      "access.token.claim": "true", "id.token.claim": "false",
                                      "introspection.token.claim": "true"}})
                print(f"client {PORTAL_CLIENT}: audience mapper for {aud} added")

print(f"realm {realm['realm']}: {len(realm.get('users', []))} users, "
      f"{len(realm['clients'])} clients, "
      f"identity providers {[i['alias'] for i in realm.get('identityProviders', [])]}")

with open(dst, "w") as f:
    json.dump(realm, f)
