#!/bin/sh
if [ "$#" -ne 1 ]; then
    echo "Illegal number of parameters."
    echo "create_slapdconf.sh slapd.conf.file"
    exit 1
fi

#output file
output=$1

if [ -f $output ]
then
    rm $output
fi


#create output
rpm -ql openldap-servers | grep '\.schema$' | sed -e '/README/d' -e 's/^/include /g' | tee  -a $output

# a temp file
temp_file=$output".tmp"

grep "core\.schema" $output > $temp_file
grep "cosine\.schema" $output >> $temp_file
grep "inetorgperson\.schema" $output >> $temp_file
grep "collective\.schema" $output >> $temp_file
grep "corba\.schema" $output >> $temp_file
grep "duaconf\.schema" $output >> $temp_file
grep "openldap\.schema" $output >> $temp_file
grep "dyngroup\.schema" $output >> $temp_file
grep "java\.schema" $output >> $temp_file
grep "misc\.schema" $output >> $temp_file
grep "nis\.schema" $output >> $temp_file
grep "ppolicy\.schema" $output >> $temp_file

# move temp_file to output
mv $temp_file $output

# add ldap
rpm -ql sudo | grep -i schema.openldap | sed 's/^/include /g' | tee -a $output