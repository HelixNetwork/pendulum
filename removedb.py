# -*- coding: utf-8 *-*
"""
Python script to make life easier when starting a fresh node.

Usage:
    # Remove all db and snapshot stuff and compiles package:
    python removedb.py
    # Remove but do not compile 
    python removedb.py -compile False
    # Compile but do not remove 
    python removedb.py -compile True 
    # Only cp 
    python removedb.py -all False -compile False -cp ~/helix/testnet/fork1/testnet-1.0
"""
import shutil
import os
from subprocess import call
import argparse

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
	description='Remove the databases and/or compile to start a node fresh'
    )

    parser.add_argument('-all',
	 metavar='Remove everything', type=lambda s: s.lower() in ['true', 't', 'yes', '1'], default='True',
	 help='Remove everything including the snapshot stuff.'
    )

    parser.add_argument('-compile',
	metavar='run mvn clean compile package', type=lambda s: s.lower() in ['true', 't', 'yes', '1'], default='True',
	help='run compile project after removing stuff'
    )

    parser.add_argument('-cp',
	metavar='cp ./src && ./target of cur wd to the dir supplied on cmd line', type=str, default='',
	help='cp -r pwd dir'
    )

    args = parser.parse_args()

    dbs = ['db', 'hxi', 'spent-addresses-log', 'spent-addresses-db', 'mainnet.log', 'mainnetdb']
    fs = ['mainnet.snapshot.meta', 'mainnet.snapshot.state', 'mainnet.snapshot.meta.bkp', 'mainnet.snapshot.state.bkp']

    if args.all:
        for dir in dbs:
            try:
                shutil.rmtree(dir)
            except:
                print("{} does not exist".format(dir))

        for f in fs:
            try:
                os.remove(f)
            except:
                print("{} does not exist".format(f))

    if args.compile:
        call('mvn clean compile package', shell=True)

    if os.path.isdir(args.cp):
        call('cp -r ./src {}'.format(args.cp), shell=True)
        call('cp -r ./target {}'.format(args.cp), shell=True)
