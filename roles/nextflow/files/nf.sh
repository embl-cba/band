# per-user Nextflow dirs; temp files in the home directory, not the node's
# small local /tmp (nextflow itself is on PATH via /usr/local/bin)
export NXF_HOME="$HOME/.nextflow"
export NXF_TEMP="$HOME/.nextflow/tmp"
