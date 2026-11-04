#!/bin/sh
# Fiji with the GPU; its models live in ~/.fiji-models (bound to the
# container's /opt/fiji/models). The SIF's default runscript is headless,
# so the desktop starts its "gui" app.
MODELS="$HOME/.fiji-models"
mkdir -p "$MODELS"

exec /bin/singularity run --nv \
    -B /scratch:/scratch \
    -B "$MODELS:/opt/fiji/models" \
    --app gui \
    /vd-containers/fiji "$@"
