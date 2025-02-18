# Contributing Guidelines

First of all, thank you so much for considering to contribute to BAND!!!
We really appreciate all kinds of contributions:
If you find problems using BAND or have an idea how to improve it, please consider [opening an issue][main-repo-issues].
In case you consider contributing code to BAND, the following guide should get you started!


## Development setup

### Fork and clone the repository

1. Fork the repository using github's web ui.

2. Clone your fork using [git][git]:

   ```bash
   git clone https://git.embl.de/<your_gitlab_handel>/create-band.git
   ```

3. Setup the upstream remote to be able to pull in any changes from the original repo

   ```bash
   git remote add upstream https://git.embl.de/grp-cbbcs/create-band.git
   ```

4. Create a branch to work on
   We follow the [GitLab Flow][gitlab-flow] where new changes are first introduced into the `develop` branch before being release on the main branch.
   If you want to work on a new feature, follow these steps:
   
   ```bash
   # go to the develop branch
   git checkout develop

   # pull in the latest changes from upstream
   git pull upstream

   # create a new branch
   git checkout -b <choose_a_good_branch_name>

   # do your changes, add and commit
   ```

### Create a ansible environment with the required dependencies 

Install Ansible by following instructions at:
https://docs.ansible.com/ansible/latest/installation_guide/intro_installation.html

### Build and test

make sure you Ansible roles are error-free

### Open a pull request

After testing your changes locally and making sure all updated files have been committed, you can push your branch:

```bash
git push --set-upstream origin <your_branch_name>
```

Go to the [main repository to open a pull request][main-repo-pulls].
Make sure to use the `develop` branch as a base.


[git]: https://git-scm.com/
[gitlab-flow]: https://docs.gitlab.com/ee/topics/gitlab_flow.html
[main-repo-issues]: https://git.embl.de/grp-cbbcs/create-band/-/issues
[main-repo-pulls]: https://git.embl.de/grp-cbbcs/create-band/-/merge_requests
