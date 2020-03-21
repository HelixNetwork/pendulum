#!/bin/bash

usage() {
    echo "Usage (local build + run): run-dev --build --image <image_name>"
    echo "Usage (git build + run): run-dev --git <branch> --image <image_name>"
    echo "Usage (pull + run): run-dev --image <image>"
    echo "Usage (local jar): run-dev --jar "
    echo "  (optional) with validator seed: --seed <validator seed>"
}

run() {
  echo "killing compose"
  docker-compose -f docker-compose-dev.yml down
  echo "spinning up compose"
  docker-compose -f docker-compose-dev.yml up -d
}

restart() {
  echo "restarting"
  docker-compose -f docker-compose-dev.yml restart relayer
  docker-compose -f docker-compose-dev.yml restart backend
}

# if no arguments supplied, display usage
if [  $# -le 0 ]
then
		usage
		exit 1
fi

build=
DOCKER_IMAGE=

while [ "$1" != "" ]; do
    case $1 in
        -i | --image )          shift
                                DOCKER_IMAGE=$1
                                ;;
        -b | --build )          build=1
                                ;;
        --restart )             restart
                                exit
                                ;;
        --local )               jar=1
                                ;;
        --git )                 git=$1
                                ;;
        --seed )                echo "$1" > .seed
                                ;;
        -h | --help )           usage
                                exit
                                ;;
        * )                     usage
                                exit 1
    esac
    shift
done

if [ "$jar" = "1" ]; then
    echo "Building jar only, skipping tests"
    mvn jar:jar -Dmaven.test.skip=true
    run
fi

if [ $DOCKER_IMAGE = "" ]
then
    usage
    exit 1
fi

export DOCKER_IMAGE=$DOCKER_IMAGE

if [ "$build" = "1" ]; then
    echo "building $DOCKER_IMAGE"
    docker build -t $DOCKER_IMAGE ../
fi

if [ "$git" != "" ]; then
   echo "building from github: $git"
   docker build -t $DOCKER_IMAGE $git
fi

run