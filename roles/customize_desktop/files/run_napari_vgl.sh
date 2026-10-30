#!/bin/bash
# Runs napari (with GPU + VirtualGL) inside the Singularity container,
# working around vglrun clobbering LD_LIBRARY_PATH for bundled NVIDIA/CUDA libs.

singularity exec --nv -B /scratch:/scratch /vd-containers/napari \
  vglrun -d egl bash -c '
NV_BASE=/opt/conda/envs/napari/lib/python3.11/site-packages/nvidia
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$NV_BASE/cufft/lib:$NV_BASE/nccl/lib:$NV_BASE/cusparse/lib:$NV_BASE/cufile/lib:$NV_BASE/nvtx/lib:$NV_BASE/cusolver/lib:$NV_BASE/cuda_nvrtc/lib:$NV_BASE/cuda_cupti/lib:$NV_BASE/cudnn/lib:$NV_BASE/nvjitlink/lib:$NV_BASE/cublas/lib:$NV_BASE/curand/lib:$NV_BASE/cuda_runtime/lib
exec /opt/conda/envs/napari/bin/napari
'

