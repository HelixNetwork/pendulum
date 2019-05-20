import shutil
import os
from subprocess import call
import argparse

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Remove the databases from the node to start fresh')
    parser.add_argument('-all', metavar='Remove everything', type=bool, default=True, help='Remove everything including the snapshot stuff')
    parser.add_argument('-compile', metavar='run mvn clean compile package', type=bool, default=False, help='run compile project after removing stuff')
    parser.add_argument('-cpr', metavar='copy and paste the src and target of a compiled version here', type=bool, default=False, help='cp -r dir1 -> pwd')
    args = parser.parse_args()
    dbs = ['db', 'hxi', 'spent-addresses-log', 'spent-addresses-db', 'mainnet.log', 'mainnetdb']
    fs = ['mainnet.snapshot.meta', 'mainnet.snapshot.state', 'mainnet.snapshot.meta.bkp', 'mainnet.snapshot.state.bkp']
    for dir in dbs:
        try:
            shutil.rmtree(dir)
        except:
            print("{} does not exist".format(dir))
    if args.all:
        for f in fs:
            try:
                os.remove(f)
            except:
                print("{} does not exist".format(f))
    if args.compile:
        call('mvn clean compile package', shell=True)
    if args.cpr:
        shutil.rmtree('src')
        shutil.rmtree('target')
        call('cp -r /home/hlx-dev/helix/testnet/fork0/testnet-1.0/src /home/hlx-dev/helix/testnet/fork1/testnet-1.0/', shell=True)
        call('cp -r /home/hlx-dev/helix/testnet/fork0/testnet-1.0/target /home/hlx-dev/helix/testnet/fork1/testnet-1.0/', shell=True)
