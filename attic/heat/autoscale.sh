#!/bin/bash

##############################################
#Config parameters
###########################################
FLAVOR="m2.xsmall"
AVAIL_ZONE="QRIScloud"
IMAGE="6ce3acb8-0880-442f-a79b-a98faf309b5b"
KEY_NAME="coersa_tern"
ANSI_GROUPS="[\"DesktopNodes\",\"default_partition\",\"centos_partition\"]"
ANSI_SSH_USER="ec2-user"
PROJECT_NAME="coclustertest"
SEC_GROUPS="default,prod_heatslurmsecgroup"
NODE_PREFIX="autocentosnode"

MIN=1				# minimum number of nodes to keep, no scale down action if total number of node <= this number
MAX=3				# maximus number of nodes to have, no scale up if total number of nodes reach this number
Lo_THRESHOLD=1		# threshold number of available node to trigger scale up
Hi_THRESHOLD=2      # threshold number of available node to trigger scale down
NODES_TO_SCALE=1    # number of nodes to create/remove

NODE_AVAIL=$(sinfo -o "%N %D %T"|grep idle|awk '{print $2}')	# current number of available nodes
TOTAL_NODES=$(sinfo -o %D|sed -n 2p)							# current number of total nodes (indcluding all states)


# if no output from NODE_AVAIL command, means current number of availble nodes is 0
if [ -z "$NODE_AVAIL" ];then
	NODE_AVAIL=0
fi

# create ssh agent for ansible
eval `ssh-agent`
ssh-add ~/.ssh/coersa_tern

##############################################################################
# Scale Up nodes, this event will be triggered if
# 1. total number of nodes smaller than $Lo_THRESHOLD 
# AND
# 2. total number of nodes after scale up is less or equal to the
#     max number of nodes  															 
# ############################################################################ 
if (( $NODE_AVAIL < $Lo_THRESHOLD && $TOTAL_NODES <= $((MAX-NODES_TO_SCALE))));then

	# create new nodes, if $NODE_AVAIL =1, create 1 new node, if NODE_AVAIL=0, create 2
	for i in `seq $NODE_AVAIL $((NODES_TO_SCALE-1))`;  
	do
		# generate random string to make node name unique 
		# (slurm need unique node name for scaling down later)
		NODE_NAME=$NODE_PREFIX-$(cat /dev/urandom | tr -cd 'a-f0-9' | head -c 10)

		#nova client to create new node
		nova boot --flavor ${FLAVOR} --availability-zone ${AVAIL_ZONE} \
		          --image ${IMAGE} --key-name ${KEY_NAME} \
	              --security-groups ${SEC_GROUPS} \
	              --meta ansible_host_groups=${ANSI_GROUPS} \
	              --meta ansible_ssh_user=${ANSI_SSH_USER} \
	              --meta project_name=${PROJECT_NAME} ${NODE_NAME} 
    done

    sleep 160s		# wait for node creation complete

	
    ./make_inventory static $PROJECT_NAME > inventory_scale		# generate updated inventory file
	
	sleep 1s
    chmod +x inventory_scale
        
	sleep 2s
    ansible-playbook -i inventory_scale make_files.yml 			# update etc/hosts and slurm.conf
    
    sleep 3s
	ansible-playbook -i inventory_scale gc_nfs_scaleup.yml      # run ansible on updated inventory

fi

#####################################################################################
# Scale down, when the following conditions are met
# 1. number of node available is >= Hi_THRESHOLD
# 2. total number of nodes after scale down is >= the minimum number of nodes to keep
#####################################################################################
if (( $NODE_AVAIL >= $Hi_THRESHOLD && $TOTAL_NODES >= $((MIN+NODES_TO_SCALE)))); then

(IFS='
'
	# output available nodes name, the number of output depends on $NODES_TO_SCALE
    for x in `sinfo -o "%n %T" |grep idle |sort |uniq |awk -F ' ' '{print $1}'|sed -n 1,"$NODES_TO_SCALE"p`;
	do
   		nova delete $x 			#delete nodes
	done

)
	
	sleep 80s					# wait for deletion to complete

    ./make_inventory static $PROJECT_NAME > inventory_scale		# update inventory after deletion

    chmod +x inventory_scale
    
    ansible-playbook -i inventory_scale make_files.yml 			# update conf files after deletion
    sleep 2s

	ansible-playbook -i inventory_scale gc_nfs_scaledown.yml     # update conf files on mgt, login and desktop nodes

fi

