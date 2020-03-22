#!/bin/bash

# This script will install the Hybris Commerce Suite, and configure it to work with the
# goodyear-ecommerce repository.  There is a basic assumption that this script will be run with
# goodyear-ecommerce as your current directory, and the hybris software will be installed in 
# sibling directories

# Base directory is the parent of the git repository
cd ..

# Download Hybris 6.4 and the MySql JDBC driver
echo "Checking..."
if [ ! -f mysql-connector-java-5.1.35.jar ]
  then
    echo "downloading mysql connector"
    curl -O repo1.maven.org/maven2/mysql/mysql-connector-java/5.1.35/mysql-connector-java-5.1.35.jar
fi

 echo "Checking..."
 if [ ! -f hybris-commerce-suite-6.4.0.5.zip ]
  then
   echo "downloading hybris commerce suite"
    curl -O http://filestore.digitas.com/hybris/hybris-commerce-suite-6.4.0.5.zip
fi

# create the hybris directories from the installation
 unzip hybris-commerce-suite-6.4.0.5.zip 

# Put the mysql driver in the hybris classpath
cp mysql-connector-java-5.1.35.jar hybris/bin/platform/lib/dbdriver

# Use our customized config directory
pushd hybris
rm -rf config
ln -s ../goodyear-ecommerce/config-macos config
popd

# Add Goodyear customization to the build
pushd hybris/bin
ln -s ../../goodyear-ecommerce/custom custom
popd

# Now do the initial build
pushd hybris/bin/platform
. ./setantenv.sh
ant clean all
popd 

echo "Install complete ..."
echo "Start the Hybris server and initialize the database"

