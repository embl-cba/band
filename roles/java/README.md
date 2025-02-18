Role Name
=========

Installs Oracle java JDK on Centos 7

Role Variables
--------------

- java_major: the major release number
- java_minor: the minor release number
- java_build: the build release number

Example:
8u45b14 --> {{major}}u{{minor}}-b{{build}}


Example Playbook
----------------

Including an example of how to use your role (for instance, with variables passed in as parameters) is always nice for users too:

    - hosts: servers
      roles:
         - java

License
-------

BSD
