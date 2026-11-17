# BAND on CESNET (e-INFRA CZ OpenStack, Brno)

Branch `cesnet`: the Euro-BioImaging service at https://band.vm.fedcloud.eu.
The VMs already exist (no Heat); inventory, `/etc/hosts` and `slurm.conf` are
maintained by hand. The de.NBI Tübingen instance is on branch `tubi`.

## Site values (not in git)

Everything specific to this site lives in files that git ignores:

| File | Holds | Template in git |
|---|---|---|
| `group_vars/all/site.yml` | host addresses (`site_hosts`, used by `inventory_cesnet`), domain, portal host name, LDAP host, address and certificate fingerprint, Keycloak realm URL and client ids, NFS address, NVIDIA driver, `portal_tls`, `portal_banner`, `slurmdbd_restore_dump` | `site.example.yml` |
| `files/etcHosts` | `/etc/hosts` for the cluster, incl. the LDAP server and the portal | `files/etcHosts.example` |
| `files/ldap-ca-<site_ldap_host>.crt` | the CA of the LDAP server's certificate (sssd checks it) | made by `ldapserver.yml` |
| `files/ssh_host_keys/<host>/` | SSH host keys saved before a rebuild (private keys vault-encrypted) | — |

Ansible loads `group_vars/all/site.yml` by itself. If it is missing, runs stop
with "'site_hosts' is undefined". Secrets are in the vault (see Secrets).

| Host | Address | OS | Role |
|---|---|---|---|
| mgmt-0 | private | Rocky 9 | slurmctld, slurmdbd, MariaDB (16 GB RAM) |
| login-0 | private | Rocky 9 | login (users' ssh, X11 forwarding), Slurm client |
| node-1 | private | Rocky 9 | desktop node, 2× GeForce RTX 2080 (8 GB) |
| node-2 … node-5 | private | Rocky 9 | desktop nodes, 2× Tesla T4 (16 GB) each |
| band-portal | private + floating IP | Ubuntu 24.04 | portal: nginx (TLS, web front end band-portal-web), Tomcat (client, Guacamole), eosc-rs, guacd, MongoDB |
| band-auth | private + floating IP | Ubuntu 24.04 | Keycloak 26 (realm `eosc`) and PostgreSQL in docker, nginx (TLS) |
| ldap | private | Rocky 9 | OpenLDAP (`ldaps://<site_ldap_host>`), `ldapserver.yml` |

Each desktop node: 24 CPUs, 56 GB RAM (`RealMemory=57344`), 80 GB root disk,
an empty 80 GB disk on `/mnt`. There is **no reverse proxy**: the portal and
Keycloak each have their own public (floating) IP and end TLS themselves.

NFS server (IPv4, not managed here): autofs mounts its `/srv/nfs` on `/nfs`
(NFSv4; `home`, `scratch`, `local`, `containers` and `modulefiles` below it are
separate exports the client crosses into). `/home`, `/scratch` and
`/usr/local/sbin` are links into `/nfs`. Keycloak's nightly backup goes to the
same server (`keycloak_backup_nfs`).

## Ansible

Use the Ansible 9 venv (ansible-core 2.16), not the system `ansible-playbook`:

    cd ~/band
    ~/ansible216/bin/ansible-playbook <playbook>.yml

`ansible.cfg` sets the inventory (`inventory_cesnet`) and the vault password
file, so neither `-i` nor `--ask-vault-pass` is needed from the repo directory.

`--limit` takes host names, groups or regexes: `node-2,node-3`,
`'DesktopNodes:!node-4'` or `'~node-[2-5]'`. `node-[2:5]` does **not** work.

Before a run against live hosts: `--check` (and `--diff` where no secret is
templated). On hosts that aren't set up yet, `--check` stops where a task needs
something an earlier task would have installed; the roles guard the common
cases (VirtualGL's EGL test, firewalld, gdm, the portal's MongoDB ping).

## Secrets (ansible-vault)

The vault password is in `~/.ansible/vault_pass_cesnet` (mode 600, **not** in
the repo). Keep a copy somewhere safe: without it the secrets can't be
decrypted. Encrypted:

- whole files: `vars/passwords.yml`, `files/slurmdbd.conf`, `files/server_ca`
  (the SSH CA private key), and the saved host keys in `files/ssh_host_keys/`
  (not in git). `copy` decrypts them on the way to the host.
- single values (`!vault`): `ldapBindDNPassword`, `ldapManagerPassword` in
  `vars/ldapConfig.yml`, `ca_pass_prase` in `vars/eosc-portal.yml`, the OIDC
  client secrets in `roles/eosc_vd_portal/vars/main.yml`, `keycloak_db_password`
  and `keycloak_admin_password` in `roles/keycloak/vars/main.yml`.

Edit or read them with:

    ~/ansible216/bin/ansible-vault edit vars/passwords.yml
    ~/ansible216/bin/ansible-vault view files/slurmdbd.conf
    echo -n 'new-secret' | ~/ansible216/bin/ansible-vault encrypt_string --stdin-name some_var

Never commit a decrypted copy, and never print a secret into a log: mask
`--diff` output of templates that hold one, and pass passwords to commands on
stdin under `no_log` (as the playbooks do).

**Working from another machine:** clone the repo, copy the vault password file
over a safe channel (never git) to `~/.ansible/vault_pass_cesnet` with mode 600,
and copy the files listed under Site values.

## Portal SSH CA

eosc-rs signs a short-lived ssh certificate for each portal login (principal =
user name, RSA/SHA-512, which Rocky 9's DEFAULT crypto policy accepts) and logs
in to login-0 with it; the cluster trusts the CA through
`/etc/ssh/sshd_config.d/40-band-portal-ca.conf` (role `portal_ca_trust`). The
CA is the only key made by hand. For a new instance, before the first
`portal.yml` run:

    openssl rand -base64 24        # the CA passphrase; keep it in a password manager
    ssh-keygen -t rsa -b 4096 -m PEM -f files/server_ca -C band-portal-ca-<site>-<date>
    ~/ansible216/bin/ansible-vault encrypt files/server_ca
    echo -n '<passphrase>' | ~/ansible216/bin/ansible-vault encrypt_string --stdin-name ca_pass_prase

`-m PEM`: the resource server reads only PEM keys. `-C` without spaces: it
can't parse a `.pub` whose comment has spaces. `files/server_ca.pub` stays
plain text in git.

## TLS and the public names

`portal_tls: local` and `keycloak_tls: local` (in `site.yml`): nginx on
band-portal and on band-auth ends TLS with a Let's Encrypt certificate in
`/etc/letsencrypt` on that VM; certbot's timer renews it through a webroot
that nginx serves on port 80 (`roles/eosc_vd_portal/tasks/tls_local.yml`,
`roles/keycloak/tasks/tls_local.yml`). The role stops if the certificate is
missing. Moving to a new VM: copy `/etc/letsencrypt` from the old one (account
and renewal settings included). New name: point it at the VM, then
`certbot certonly --standalone -d <name>` with nginx stopped. With
`portal_tls: local`, Tomcat (8080) and eosc-rs (9001) listen on 127.0.0.1 only.

The names stay; the floating IPs move. To switch to new portal and Keycloak
VMs, move **both** floating IPs together (the portal checks tokens against the
Keycloak its `/etc/hosts` points to). Rollback: move them back.

## Keycloak (band-auth)

`keycloak.yml` (role `keycloak`) runs Keycloak 26 and PostgreSQL in docker on
`AuthNodes`. The public URL stays `https://<auth host>/auth`, so the issuer,
the portal's client settings and the redirect URIs registered at Google and
LS AAI don't change.

- The image is built on the VM with the registration step from
  [band-keycloak-flow-extension](https://github.com/embl-cba/band-keycloak-flow-extension)
  and the login theme from
  [band-keycloak-theme](https://github.com/embl-cba/band-keycloak-theme)
  (cesnet: `keycloak_theme_version: v1.0.0-cesnet`, with the CESNET logo),
  each a pinned tag with its sha256, and the LDAP server's certificate,
  fetched at deploy time and used only with the fingerprint
  `site_ldap_cert_sha256`.
- Data: PostgreSQL in `/opt/keycloak/postgres`; both containers restart
  unless stopped. Secrets reach them through root-only env files in
  `/opt/keycloak`. The `admin` password only takes effect on the very first
  start; later change it in the admin console.
- Realm: imported once from the old Keycloak's export, adapted by
  `roles/keycloak/files/prepare-realm.py`:

      ansible-playbook keycloak.yml --tags keycloak_import \
          -e keycloak_import_file=/path/eosc-realm.json [-e keycloak_import_override=true]

  The export holds users, keys and client secrets: keep it out of git.
- Backup: `keycloak-backup.timer` makes a nightly `pg_dump` to
  `/opt/keycloak/backups/` (root only, kept 14 days) and copies it to the NFS
  server. Restore (same Keycloak version):

      sudo docker stop keycloak
      sudo docker exec keycloak-postgres psql -U keycloak -d postgres \
          -c 'DROP DATABASE keycloak' -c 'CREATE DATABASE keycloak'
      sudo docker exec -i keycloak-postgres pg_restore -U keycloak -d keycloak \
          --no-owner < /opt/keycloak/backups/keycloak-<date>-<time>.dump
      sudo docker start keycloak

Docker on these VMs needs `dns` and `mtu` in `/etc/docker/daemon.json` (the
role sets them).

## Hand-maintained files

| File | What |
|---|---|
| `inventory_cesnet` | hosts and groups (addresses from `site_hosts`) |
| `files/etcHosts` | installed as `/etc/hosts` on the cluster |
| `files/slurm.conf` | `ClusterName=mbp`, nodes `node-[1-5]` with `Gres=gpu:2`, `RealMemory=57344`, partitions `batch` and `rocky`, `FirstJobId=10000` |
| `files/slurmdbd.conf` | installed mode 600; vaulted |
| `vars/slurm.yml` | Slurm 25.11.4, munge 0.5.18, NHC 1.5, MariaDB settings |
| `vars/eosc-portal.yml` | portal settings and the cesnet changes to the web front end |
| `roles/customize_desktop/vars/main.yml` | the desktop launchers (one entry per app) |

## Rebuilding the cluster (as done in November 2026)

All 7 cluster VMs were rebuilt with a clean Rocky 9 in one window, as a new
instance with the new portal, Keycloak and LDAP. In that order:

1. **Save what the repo can't recreate.** Nothing on the cluster's local disks
   is user data (homes, containers, modulefiles are on NFS), except:
   - login-0's SSH host keys, so users' `known_hosts` stay valid:
     `ansible login-0 -b -m fetch -a 'src=/etc/ssh/ssh_host_<type>_key dest=files/ssh_host_keys/login-0/ flat=yes'`
     for each key and `.pub`, then `ansible-vault encrypt files/ssh_host_keys/login-0/*_key`.
     `0_base.yml` puts them back.
   - the Slurm accounting database: on mgmt-0, a `mysqldump --single-transaction
     --databases slurm_acct_db | gzip` (root's MariaDB password through a
     temporary root-only option file, never on the command line), copied to
     the ansible host, outside the repo. Take the final dump when no node can
     run jobs any more, just before mgmt-0 goes.
2. **Rebuild** with `openstack server rebuild --image rockylinux-9-x86_64`
   (the undated image is the newest; check the release after boot). The IP
   stays, the disk is wiped.
3. **Check each new host key** against the fingerprints cloud-init prints in
   `openstack console log show <host>`, then replace the entry in
   `~/.ssh/known_hosts`.
4. **Update everything once**: `dnf upgrade` and one reboot on all hosts,
   before step 2, so the NVIDIA module is built for the final kernel.
5. **Run the playbooks** 0–7 (below). GPU steps first on one node of each GPU
   type (node-1 and node-2), with `-e nvidia_allow_reboot=true`
   (`site.yml` keeps it `false` for live nodes). For the job history, set
   `slurmdbd_restore_dump` before step 5 (see there).
6. **Portal**: `portal.yml`; then move the floating IPs (see TLS).

## Deploy order

`cluster_installation.yml` runs steps 0-7 in order. Each step is also its own
playbook, so a step can be rerun alone. They target only
`ManagementNodes:LoginNodes:DesktopNodes` (step 1 also `LdapNodes`).

0. `0_base.yml`: `/etc/hosts`, short host name (cloud-init is told to keep
   it), root password (console only), chrony, crypto policy DEFAULT, sshd
   drop-in `/etc/ssh/sshd_config.d/01-band.conf` (keys only, no root login,
   X11 forwarding only on LoginNodes; checked with `sshd -t`, then reloaded),
   and saved host keys from `files/ssh_host_keys/<host>/`.
1. `1_disable_selinux.yml`: SELinux off on the desktop nodes and the LDAP
   server (reboots and waits). mgmt-0 and login-0 keep it enforcing.
2. `2_installgpu_rocky9.yml`: NVIDIA driver (`nvidia_driver_version`,
   620.70.06, open kernel modules for these Turing GPUs) from the runfile with
   DKMS; nouveau blocked in modprobe, initramfs and kernel command line. It
   stops instead of rebooting unless `nvidia_allow_reboot` is true, checks
   `/boot` has 300 MB free before dracut, reboots after a new driver (the GPUs
   only work after that) and fails if `nvidia-smi` lists no GPU.
3. `3_ldapclient.yml`: sssd against `ldaps://<site_ldap_host>`, verifying the
   server certificate with `files/ldap-ca-<host>.crt`.
4. `4_nfs_installation.yml`: autofs mounts `/nfs`; `rocky`'s home moves to
   `/local_home`, then `/home`, `/scratch` and `/usr/local/sbin` become links
   into `/nfs`. Where SELinux is on, `use_nfs_home_dirs` (else sshd can't read
   `~/.ssh/authorized_keys`).
5. `5_slurm_installation.yml`: MariaDB (InnoDB settings for slurmdbd, 4 GB
   buffer pool) and slurmdbd on mgmt-0, munge 0.5.18 and Slurm 25.11.4 built
   from source (`make -j`), NHC 1.5, slurmctld, slurmd on the nodes, client on
   login-0. With `slurmdbd_restore_dump` set, the dump is loaded into the
   empty `slurm_acct_db` before slurmdbd's first start (never over existing
   tables; slurmdbd converts them if Slurm is newer). Keep `ClusterName` and
   set `FirstJobId` above the old job ids. Users need a Slurm account
   (`AccountingStorageEnforce=limits`); root can't run jobs (`DisableRootJobs`).
6. `6_provision_account.yml`: `/usr/local/sbin/provision_account.py` and a
   3-minute cron job on login-0: a home directory and a Slurm user in account
   `default` for every LDAP user, and a welcome mail.
7. `7_desktop.yml`, one part alone with `--tags`:
   - `vgl`: VirtualGL 3.1.5 with the EGL back end. A udev rule opens the
     GPUs' DRI devices; `/usr/local/bin/vgl-display` picks the card of the
     job's GPU (`SLURM_JOB_GPUS`) for `VGL_DISPLAY`. The role checks every GPU
     has a DRI card and renders with EGL.
   - `lmod`: Lmod with `MODULEPATH=/nfs/modulefiles` (login and desktop nodes).
   - `vnc`: TigerVNC 1.15 with the GNOME flashback/metacity session; Xvnc
     listens on localhost only (the portal reaches it through an ssh tunnel);
     no gdm, text boot target, firewalld off.
   - `s3`: s3fs-fuse and the "Mount S3 Buckets" GUI; `slurm.epilog` unmounts
     a user's buckets when their last job on the node ends.
   - `apps`: apptainer, Java, firefox, tools; rclone and nextflow on the share
     (`/nfs/local/bin`); dconf settings; the menus and launchers. The apps are
     containers in `/nfs/containers`; a new app needs its container there and
     an entry in `desktop_launchers`.

Not part of the full run:
- `keycloak.yml` (band-auth), `ldapserver.yml` (LdapNodes).
- `portal.yml`: the portal on band-portal, the CA trust on login-0 and the
  desktop nodes, and `vncpasswd` on login-0 (the portal makes each desktop's
  VNC password there). Desktops start with `-SecurityTypes VncAuth`; the
  Guacamole extension passes the password. No binary is in git: the role
  downloads each one, checked against the sha256 in
  `roles/eosc_vd_portal/vars/main.yml`. The front end is a release of
  [band-portal-web](https://github.com/embl-cba/band-portal-web); its pages
  are compiled in, so cesnet changes them on install (`--tags portal_web`):
  `portal_banner` (the red banner; `site.yml`), `portal_web_files` (the
  CESNET logos) and `portal_web_replacements` (header and acknowledgement
  logos, Resources and Status pages) in `vars/eosc-portal.yml`. Undoing a
  replacement needs the release unpacked again (delete
  `/var/www/html/band-portal-web-<version>` and rerun).

## Gotchas

- **After a reboot a node stays DOWN** ("Node unexpectedly rebooted"). Put it
  back with `scontrol update nodename=<node> state=resume`.
- **Out of service:** `scontrol update nodename=<node> state=drain reason="..."`,
  and run the playbooks with `--limit '!<node>'` until it's back.
- **A GPU "lost from the bus"** (`lspci` shows `rev ff`, `NV_ERR_GPU_IS_LOST`
  in the kernel log) is the passed-through card or the hypervisor, not the
  driver. A reboot inside the VM doesn't help; try `openstack server stop`
  and `start`, else ask CESNET (node-4, November 2026).
- **The NVIDIA installer loads its module before the firmware is in place**:
  until the next boot, `nvidia-smi` says "No devices were found" (with exit
  code 0) and nvidia-drm logs "Failed to allocate NvKmsKapiDevice".
- **No firewall on the nodes**; the OpenStack security groups do that job.
  firewalld blocks slurmd (6818) and srun's ports.
- **cloud-init resets the host name** unless
  `/etc/cloud/cloud.cfg.d/99-preserve-hostname.cfg` says
  `preserve_hostname: true` (`0_base.yml`).
- **A fresh host's first Slurm run**: the `slurm` user must exist before
  `slurmdbd.conf` is given to it (`slurmdb-config` creates it).
- **NHC 1.5** ships a `.tar.gz` (`nhc_tarball`) with a ready `nhc` script;
  the build keys on the `Makefile`.
- Ubuntu's `apt-daily` can hang for days on these VMs and hold the apt lock;
  stop it before a `portal.yml` or `keycloak.yml` run if apt is locked.
- Testing jobs as root fails (`DisableRootJobs`); use a user with a Slurm
  account, and remove a test account afterwards.

The previous versions of this file are `attic/NEW_INSTANCE.old.md` and the
Tübingen one on branch `tubi`.
