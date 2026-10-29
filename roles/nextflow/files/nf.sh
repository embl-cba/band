#!/bin/bash

# Add Nextflow to PATH
export PATH=/opt/nf:$PATH

# Set per-user Nextflow config dirs
export NXF_HOME="$HOME/.nextflow"
export NXF_TEMP="$HOME/.nextflow/tmp"
