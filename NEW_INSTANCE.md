# BAND on de.NBI Tübingen (tubi)

The VMs already exist (no Heat). Inventory, `/etc/hosts` and `slurm.conf` are
maintained by hand.

## Site values (not in git)

Everything specific to this site lives in two files that git ignores:

| File | Holds | Template in git |
|---|---|---|
| `group_vars/all/site.yml` | host addresses (`site_hosts`, used by `inventory_tubi`), domain, portal host name, LDAP host, Keycloak realm URL and client ids | `site.example.yml` |
| `files/etcHosts` | `/etc/hosts` for all nodes, incl. the LDAP server and `nfs-server` | `files/etcHosts.example` |

Ansible loads `group_vars/all/site.yml` by itself (it sits next to the
inventory and the playbooks). If it is missing, runs stop with
"'site_hosts' is undefined". The SMTP user, the welcome-mail sender and the
other secrets are in the vaulted `vars/passwords.yml` (see Secrets).

| Host | IP | OS | Role |
|---|---|---|---|
| mgmt-0 | private | Rocky 9 | slurmctld, slurmdbd, MariaDB |
| login-0 | private | Rocky 9 | login, VNC |
| node-1 … node-8 | private | Rocky 9 | desktop nodes, 1× Tesla V100-SXM2-32GB each |
| band-portal | private | Ubuntu 20.04 | portal |

Addresses are in `inventory_tubi` / `files/etcHosts`.

External services: NFS server `nfs-server` (its IPv6 address is in
`files/etcHosts`). autofs mounts its exports `/mnt/vd_shares` on `/nfs`,
`/scratch` (5.9 TB) on `/scratch` and `/vd-containers` (container images used
by the desktop launchers) on `/vd-containers`.
LDAP `ldaps://<site_ldap_host>` (no DNS record; its address is in
`files/etcHosts`).

## Ansible

Use the Ansible 9 venv (ansible-core 2.16), not the system `ansible-playbook`
(2.21 can't manage band-portal's Python 3.8):

    cd /home/ubuntu/create-band-tubi
    ~/ansible216/bin/ansible-playbook <playbook>.yml

`ansible.cfg` sets the inventory (`inventory_tubi`) and the vault password
file, so neither `-i` nor `--ask-vault-pass` is needed when you run from the
repo directory.

`--limit` takes host names, groups or regexes: `node-2,node-3`,
`'DesktopNodes:!node-1'` or `'~node-[2-8]'`. `node-[2:8]` does **not** work
(it means "items 2–8 of a group called node-").

## Secrets (ansible-vault)

The vault password is in `~/.ansible/vault_pass_tubi` (mode 600, **not** in
the repo). Keep a copy somewhere safe: without it the secrets below can't be
decrypted. Encrypted:

- whole files: `vars/passwords.yml`, `files/slurmdbd.conf`, `files/server_ca`
  (the SSH CA private key). `copy` decrypts them on the way to the host.
- single values (`!vault`): `ldapBindDNPassword`, `ldapManagerPassword` in
  `vars/ldapConfig.yml` and `roles/eosc_ldapserver/vars/main.yml`
  (plus `testPassword`), `ca_pass_prase` in `vars/eosc-portal.yml`, the OIDC
  client secrets in `roles/eosc_vd_portal/vars/main.yml`.

Edit or read them with:

    ~/ansible216/bin/ansible-vault edit vars/passwords.yml
    ~/ansible216/bin/ansible-vault view files/slurmdbd.conf
    echo -n 'new-secret' | ~/ansible216/bin/ansible-vault encrypt_string --stdin-name some_var

Never commit a decrypted copy.

## Hand-maintained files

| File | What |
|---|---|
| `inventory_tubi` | hosts, IPs, groups |
| `files/etcHosts` | installed as `/etc/hosts` on all nodes |
| `files/slurm.conf` | nodes `node-[1-8]`, `Gres=gpu:1`, partitions `batch` and `rocky` |
| `files/slurmdbd.conf` | installed mode 600; vaulted (`ansible-vault edit`) |
| `vars/*.yml` | domain, LDAP, Slurm settings, passwords |

`attic/make_files/make_files.yml` would regenerate `etcHosts` and `slurm.conf`
from templates and overwrite the hand-made versions. Don't run it.

## Deploy order

`cluster_installation.yml` runs steps 0-10 in order (`import_playbook`). Each
step is also its own playbook, so a step can be rerun alone. All of them target
only `ManagementNodes:LoginNodes:DesktopNodes`, never band-portal.

0. `0_base.yml`: `/etc/hosts`, short host name (persistent; cloud-init is told
   to keep it), root password, time sync, sshd password-login settings. Logging is left as Rocky 9 ships it.
1. `1_disable_selinux.yml`: reboots every node it changes.
2. `2_installgpu_rocky9.yml`: NVIDIA 580 driver (proprietary module, DKMS) on
   DesktopNodes. Reboots each node once.
3. `3_ldapclient.yml`: sssd against `ldaps://<site_ldap_host>`.
4. `4_nfs_installation.yml`: autofs mounts `/nfs`, `/scratch` and
   `/vd-containers`; `rocky`'s home moves to `/local_home`, then `/home` and
   `/usr/local/sbin` are linked into `/nfs`.
5. `5_slurm_installation.yml`: hosts file, MariaDB, slurmdbd and slurmctld on
   mgmt-0, slurmd on node-1..8, client on login-0; munge + Slurm 25.11.4 built
   from source. Users need a Slurm account (`AccountingStorageEnforce=limits`).
6. `6_provision_account.yml`: installs `/usr/local/sbin/provision_account.py`
   (on the share, root-only) and a 3-minute cron job on login-0. It makes a
   home directory and a Slurm user in account `default` for every LDAP user;
   `--dry-run` shows what it would do. Newly provisioned users get a welcome
   mail (`provision_send_email: True` in the playbook; SMTP settings in
   `vars/passwords.yml`).
7. `7_installvgl.yml`: VirtualGL, EGL back end, for GPU-rendered OpenGL in VNC
   desktops (`vglrun <app>`).
8. `8_lmod.yml`: Lmod on login and desktop nodes.
9. `9_vncserver.yml`: TigerVNC with the GNOME flashback/metacity session on
   the desktop nodes. Keeps plymouth out of the initramfs (`/boot` is small)
   and turns off firewalld, which the GUI group installs enabled.
10. `10_s3fs.yml`: s3fs-fuse on the desktop nodes. `slurm.epilog` unmounts a
   user's `fuse.s3fs` mounts (matched by the mount's `user_id`, nothing else),
   removes the empty mount point, deletes `~/.passwd-s3fs` once the user has no
   job left anywhere, and cleans their X/pulse files in `/tmp` once they have
   no job left on the node.

Not part of the full run:
- Portal: `eoscservers.yml` (now also `eosc_prepare_loginnode` on login-0),
  `eoscportal.yml`. Not reviewed for tubi yet.
- `ldapserver.yml` (the LDAP server itself). Its `ldapserver` group is not in
  `inventory_tubi`; add one before using it.
- The pre-split all-in-one playbook is kept as `attic/cluster_installation.old.yml`.

## Gotchas

- **After a reboot a node stays DOWN** ("Node unexpectedly rebooted",
  `ReturnToService=1`, no `RebootProgram`). Put it back with
  `scontrol update nodename=node-[1-8] state=resume` on login-0 or mgmt-0.
  Reboot mgmt-0 first if rebooting everything.
- **No firewall on the nodes.** The OpenStack security groups do that job.
  firewalld (pulled in by "Server with GUI") blocks slurmd's port 6818 and
  srun's ports: the node shows `idle*`/`down*` and jobs never start.
  `9_vncserver.yml` disables it.
- **cloud-init resets the host name** to the OpenStack FQDN (`node-1.<domain>`) on every boot
  unless `/etc/cloud/cloud.cfg.d/99-preserve-hostname.cfg` says
  `preserve_hostname: true`; `0_base.yml` installs that.

- **nouveau must never load on the V100 nodes.** It crashes probing the GPU,
  after which `nvidia-smi` hangs, and on one node it caused a kernel panic at
  boot. `rocky9gpu` blocks it in `/etc/modprobe.d`, in the initramfs
  (`/etc/dracut.conf.d/omit-nouveau.conf`) and on the kernel command line. A
  node that panics needs `rd.driver.blacklist=nouveau modprobe.blacklist=nouveau`
  added once at the GRUB prompt from the OpenStack console, then a run of the role.
- **`/boot` is only ~936 MB.** The NVIDIA driver is kept out of the initramfs
  (it adds ~260 MB) and `rocky9gpu` checks every kernel's initramfs after a
  rebuild. Don't remove those settings.
- Check GPU state with `modinfo -F version nvidia` and `lsmod | grep nouveau`.
  Only run `nvidia-smi` with a timeout. `lspci` isn't installed; use
  `/sys/bus/pci/devices/*/vendor`.
- The NFS server only exports to the cluster's IPv6 range; mounting its IPv4
  address is denied, so `automount` mounts it over
  IPv6 (`proto=tcp6`). The share's `home/` holds existing users' homes and its
  `slurmstate1/` belongs to an older cluster, so the Slurm state stays local on
  mgmt-0 (`usesharedstatedir=False`).
- Nodes: `RealMemory=82000` (slurmd -C reports 84955), `DefMemPerCPU=10000`.
  Job mail goes through s-nail on mgmt-0 (`/etc/s-nail.rc`, SMTP settings from
  `vars/passwords.yml`); users need `--mail-user=<address>`, since Slurm user
  names are not mail addresses.
- NHC (cron every 5 min) uses the short host name (`HOSTNAME` in
  `/etc/sysconfig/nhc`); its stock sshd check never matched on Rocky 9 and was
  fixed to `-m "sshd:"`. Without both fixes it would drain every node.
- Slurm 25.11: slurmctld must run as `SlurmUser` (`User=slurm` in its unit),
  slurmd needs `Delegate=yes` for cgroup v2, munge 0.5.18 needs
  `<munge_dir>/var/run/munge` to exist.
- `vars/eosc-servers.yml` overrides `ansible_os_family` to `Rocky`. Conditions
  that need the real value must use `ansible_facts['os_family']`.

The previous version of this file (from the copied instance) is in
`attic/NEW_INSTANCE.old.md`.
