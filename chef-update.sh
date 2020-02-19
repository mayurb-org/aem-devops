#!/bin/bash

  # ensure the .chef directory is linked
  if [ ! -h .chef ]; then ln -s ../.chef .chef; fi;
  
  # define the chef server name 
  CHEF_SERV_NAME=icecream
  
  # ensure the correct environment versions are linked as well
  DIRS=(data_bags environments nodes)
  for dir in ${DIRS[@]}; do
    if [ ! -d $dir ] && [ ! -h $dir ]; then ln -s $dir.$CHEF_SERV_NAME $dir; fi;
  done

  if [ -f Berksfile ]; then
    # ensure local cookbooks are installed/updated
    cb=$(ls site-cookbooks/); berks update $cb;

    # update Chef server
    # berks upload --no-ssl-verify --force --no-freeze;
    # install local cookbooks directory via berkshelf
    berks vendor cookbooks;

  elif [ -f Cheffile ]; then
    # install local cookbooks directory via librarian
    librarian-chef install

  fi

  if [ ! -d .chef/trusted_certs ]; then
    # ensure we trust the chef server
    knife ssl fetch
  fi

  # update Chef server
  knife upload /