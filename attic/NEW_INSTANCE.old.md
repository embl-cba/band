# Setting up this BAND instance

Copied from `create-band-tub`. Every value tied to the old site (cloud IDs,
hostnames, LDAP, Keycloak) was replaced with a `CHANGEME_*` placeholder.
Secrets (munge key, DB passwords, SSH CA keypair, root hash) were kept as-is.

List what is still to fill in:

    grep -rn CHANGEME .

## 1. OpenStack (`create_cluster.yaml`, `mgmtnode.yaml`)

- Image IDs (Rocky 8, Ubuntu 20.04), network ID, keypair, `project_name`
- Flavors for mgmt / login / desktop (GPU) / portal nodes; `count` of desktop nodes
- Security groups referenced by the template must exist in the project:
  `default`, `ssh`, `http`, `heatslurmsecgroup`, `heatmysqlsecgroup`

Create the stack, then build the inventory from VM metadata:

    source <openstack-rc>
    openstack stack create -t create_cluster.yaml -e resource_registry.yaml <stack>
    ./make_inventory static <project_name> > inventory && chmod +x inventory

## 2. Site settings

| File | Placeholder |
|---|---|
| `vars/names.yml` | `CHANGEME_DOMAIN` |
| `vars/eosc-portal.yml` | `CHANGEME_PORTAL_FQDN` (public DNS name of the portal) |
| `vars/slurm.yml` | `CHANGEME_CLUSTER_NAME` |
| `roles/automount/vars/nfs.yml` | `CHANGEME_NFS_SERVER_IP` (must export `/nfs/home`, `/nfs/scratch`, `/nfs/local/sbin`) |

## 3. New LDAP server

1. Fill `ldaphost` and `roles/eosc_ldapserver/vars/main.yml` (FQDN, org, email, passwords).
2. `ansible-playbook -i ldaphost ldapserver.yml` (this fetches the new CA into `files/rootca.crt`).
3. Fill `vars/ldapConfig.yml`: server IP/FQDN line, `ldapURI` (e.g. `ldaps://<fqdn>`),
   base DN, bind password, `ldapManagerPassword`, and paste `files/rootca.crt` into
   `ldapCaCertContents`.

## 4. New Keycloak realm (`roles/eosc_vd_portal/vars/main.yml`)

- `eosc_oidc_realm_url`, e.g. `https://auth.example.org/auth/realms/eosc`
- Create confidential clients for the portal, the resource server and the group manager and put their
  secrets in the three `CHANGEME_*_CLIENT_SECRET` values
- Redirect URI for the portal client: `https://<portal fqdn>/client/api/oauth/callback/eosc`

## 5. Deploy

    ansible-playbook -i inventory make_files.yml          # generates /etc/hosts, slurm.conf
    ansible-playbook -i inventory cluster_installation.yml
    ansible-playbook -i inventory eoscservers.yml
    ansible-playbook -i inventory eoscportal.yml

`files/etcHosts`, `files/slurm.conf`, `files/slurmdbd.conf` are still the old site's
copies; `make_files.yml` regenerates them.
