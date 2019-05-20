import shutil
import os
#from subprocess import call
import argparse

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Organize the logs directory into dates')
    parser.add_argument('-all', metavar='Organize everything', type=bool, default=True, help='Organize everything including the snapshot stuff')
    args = parser.parse_args()
    # the argparser is actually pointless, but maybe we might use it later
    for f in os.listdir('./logs'):
        if os.path.isfile(os.path.join('./logs', f)) & os.path.join('./logs', f).endswith('.log'):
            if not os.path.isdir(os.path.join('./logs', f.split('__')[1][:-9]) ):
                #print(os.path.join('./logs', f.split('__')[1][:-9]))
                os.mkdir(os.path.join('./logs', f.split('__')[1][:-9]))
            shutil.move(os.path.join('./logs', f),  os.path.join('./logs', f.split('__')[1][:-9], f))
            #print(os.path.join('./logs', f),  os.path.join('./logs', f.split('__')[1][:-9], f))
