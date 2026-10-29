 # Bioimage ANalysis Desktop (BAND) #
 The BAND is a cloud based virtual desktop service accessible with a web browser so that you can work on your data from anywhere with an internet connection.

## Current Deployment

 - denbi Tuebingen node:
https://bandv1.denbi.uni-tuebingen.de/
 - Eurobioimaging service:
https://band.vm.fedcloud.eu
 - Vlaams Instituut voor Biotechnologie (VIB) in Flanders, Belgium

## Training materials ##
- VIB HPC course: https://github.com/vib-bic-training/2024_Bioimaging_data_analysis_on_HPC/blob/main/Chapters/Chapter03.md
- NEUBIAS cloud bioimage analysis: https://neubias.github.io/training-resources/cloud-based-analysis-course/index.html

## Deploy your own BAND  ## 
  Please refer to the [WIKI page](https://git.embl.de/ysun/create-band/-/wikis/Home) for setting up BAND

## This repository ##
The Ansible code here is the deployment of the de.NBI Tübingen node
(branch `tubi`; `main` currently follows it): Rocky Linux 9, Slurm 25.11,
GPU desktop nodes with NVIDIA drivers and VirtualGL, LDAP accounts, NFS homes,
TigerVNC desktops and S3 bucket mounting. The cluster is installed by numbered
playbooks `0_base.yml` … `10_s3fs.yml`, which `cluster_installation.yml` runs
in order. [NEW_INSTANCE.md](NEW_INSTANCE.md) describes each step, the
requirements and the known pitfalls.

Not in the repository, by design. Placeholders are pushed instead:
- **Site values.** Host IP addresses, server names (cluster domain, portal,
  LDAP server), the Keycloak realm URL and client ids, and the portal's admin
  and support e-mail addresses and SMTP host are set in
  `group_vars/all/site.yml`, which git ignores. `inventory_tubi` and the vars
  files only refer to them. To deploy, copy `site.example.yml` to
  `group_vars/all/site.yml` and fill in your own values.
- **`/etc/hosts`.** `files/etcHosts` is ignored too; start from
  `files/etcHosts.example`.
- **The vault password.** Passwords, keys and client secrets, as well as the
  SMTP user name and the welcome-mail sender, are encrypted with ansible-vault
  (`vars/passwords.yml`). `ansible.cfg` expects the password in
  `~/.ansible/vault_pass_tubi`. For a new site, replace the encrypted values
  with your own, encrypted with your own vault password (see the
  "Secrets" section of NEW_INSTANCE.md).


The code is partly based on https://github.com/monash-merc/ansible_cluster_in_a_box

## Courses that used BAND
|Course Name| No. of Participants  | Location | Date|
|--|--|--|--|
|Volume electron microscopy by automated serial SEM |8|Germany|23-28 Nov 2025|
|GloBIAS Training School|52|Kobe, Japan|25-29 Oct 2025|
|FISHing for RNAs: classical to single molecule approaches |20|Germany|31 - 5 Sept 2025|
|Hands-on workshop on: Basics of cryo-FIB miling and in situ cryo-electron tomography|8|Germany|13-17 Oct|
|Fundamentals of Light Microscopy |17|South Africa|July 2025|
|Mycology Bioimaging: Imaging the fungal cell|17|South Africa|July 2025|
|An interactive introduction into the NGFF-ecosystem||Germany|7-11 April 2025|
|Introduction to image segmentation with Microscopy Image Browser|10|Berlin Germany|Jan 2025|
|Intro to BioImage Analysis with Python for Life Scientists|60|Sweden, France, Portugal|Oct 2024|
|Power hour on AI tools and public data archive|20|Belgium|March 2024|
|HSC Deep Learning Bioimage Analysis Course|21|European Neuroscience Institute Goettingen|Feb 2024|
|i3S: Advanced Bioimage internal workshop|30|Portugal,Virtual|Oct 2023|	
|EMBO Practical course: FISHing for RNAs: classical to single molecule approaches|25|Germany,Italy,Virtual|Oct 2023|
|Cajal Advanced Neuroscience training|21|France|Oct 2023|
|EMBO Practical course: Advanced methods in bioimage analysis|26|Germany|Sept 2023|
|EMBO Practical course: Drosophila genetics and genomics|25|Germany|Jul 2023|
|EMBO Practical course: Imaging-based spatial-omics|20|Italy,Virtual|Jun 2023|
|EMBL-MPI-FMI: Beginer course for python based image analysis using skimage and napari|50|Germany,Virtual|Jun 2023|	
|EMBL course: Deep learning for image analysis|24|Germany|Jun 2023|
|EMBO Practical course: Current method in cell biology|24|Germany|Sept 2022|
|EMBO Practical course: advances in cryo-electron microscopy and 3D image processing|24|Germany|Aug 2022|	
|NEUBIAS Defragmentation training school|25|Virtual|Aug 2022|
|Switzerland Image and Data Analysis School (ZIDAS)|21|Switzerland|Jun 2022|
|EMBL course: Deep learning for image analysis|21|Virtual|Jan 2022|
|EMBL course: Deep learning for image analysis|24|Virtual|Feb 2021|
|EMBL Bio-IT course: Introduction to deep learning|~10|Virtual,Germany|Dec 2021; Feb 2022|
|EMBL Bio-IT course: FAIR in (biological) practice|~10|Vritual, Germany|Jun 2022|

## Contributing
[Contributing Guidelines](https://github.com/embl-cba/band/blob/main/CONTRIBUTING.md)

## Citation

> Sun, Y., Heriche, J.-K., & Kutra, D. (2024). The BAND- a cloud-based virtual desktop for bioimage analysis. Zenodo. [https://doi.org/10.5281/zenodo.12699364](https://doi.org/10.5281/zenodo.12699364)

## License  ## 
[GPL](https://github.com/embl-cba/band/blob/main/LICENSE)

## Acknowledgement ##
This project is funded by the Deutsche Forschungsgemeinschaft (DFG, German Research Foundation) under the National Research Data Infrastructure – NFDI 46/1 – 501864659

## Testimonials ##
> _"There are always course participants who do not download and set up the necessary software in advance, and catching them up consumes valuable teaching time. BAND is a game changer. Having the machines ready and set up has allowed us to get straight to teaching, and provides a more uniform experience for all participants. Knowing the computer environment ahead of time also makes it possible to prepare a much more specific and streamlined course programme"_


> _"I've used the BAND platform for several training courses until now, and each time it's been a great experience. Having instant access to GPU-equipped virtual machines is invaluable when working with modern deep learning segmentation methods. It removes all the usual hardware barriers and lets us focus on teaching state-of-the-art approaches. The support team was excellent too, responding quickly even outside regular office hours. It's exactly the kind of infrastructure we need for effective training courses"_
