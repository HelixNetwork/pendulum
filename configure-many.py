# -*- coding: utf-8 *-*
# Module: Topology & Neighbors
# This module will handle the static topology of the network
# and generate configuration files for the jars.
# Note that the term, "static", is important, since the overall goal
# should be to acheive a simulation that allows for node joining and
# leaving the network.
import shutil
import os
from subprocess import call
import argparse
import networkx as nx

class SmallWorldTopology():
    def __init__(self, numNodes, averageNeighborsPerNode, probabilityAddingAdditionalNeighbors):
        self.nNeighbors = averageNeighborsPerNode
        self.pNewEdge = probabilityAddingAdditionalNeighbors
        self.overlay = nx.watts_strogatz_graph(n=numNodes, k=self.nNeighbors, p=self.pNewEdge)

    def get_neighbor(self, nodeID):
        if list(self.overlay.neighbors(nodeID)):
            return list(self.overlay.neighbors(nodeID))

    def get_matrix(self):
        self.overlay_matrix = nx.to_numpy_matrix(self.overlay)

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='')
    parser.add_argument('-apiPortStart', metavar='Port to start iterating at', type=int, default=14000, help='')
    parser.add_argument('-udpReceiverPortStart', metavar='UDP Receiver port to start iterating at', type=int, default=24000, help='')
    parser.add_argument('-tcpReceiverPortStart', metavar='TCP Receiver port to start iterating at', type=int, default=34000, help='')
    parser.add_argument('-zmqIpcStart', metavar='ZMQ ipc port to start iterating at', type=int, default=1, help='')
    parser.add_argument('-zmqPortStart', metavar='ZMQ port to start iterating at', type=int, default=5550, help='')
    parser.add_argument('-numNodes', metavar='Number of nodes.', type=int, default=10, help='')
    args = parser.parse_args()
    apiPortStart = args.apiPortStart
    udpReceiverPortStart = args.udpReceiverPortStart
    tcpReceiverPortStart = args.tcpReceiverPortStart
    inet = "127.0.0.1"
    tcpInetPort = udpReceiverPortStart
    udpInetPort = udpReceiverPortStart
    zmqIpcStart = args.zmqIpcStart
    zmqPortStart = args.zmqPortStart
    numNodes = args.numNodes
    topology = SmallWorldTopology(numNodes, averageNeighborsPerNode=8, probabilityAddingAdditionalNeighbors=0.1)
    for i in range(numNodes):
        config =  """#NODE {} CONFIG
API_PORT = {}
API_HOST = localhost
REMOTE_LIMIT_API = addNeighbors, getNeighbors, removeNeighbors, attachToTangle, interruptAttachingToTangle
MAX_FIND_TRANSACTIONS = 100_000
MAX_REQUESTS_LIST = 1_000
MAX_GET_BYTES = 10_000
MAX_BODY_LENGTH = 1_000_000
#REMOTE_AUTH =
MS_DELAY = 0
UDP_RECEIVER_PORT = {}
TCP_RECEIVER_PORT = {}
NEIGHBORS
P_REMOVE_REQUEST = 0.01
SEND_LIMIT = -1
MAX_PEERS = 0
DNS_REFRESHER_ENABLED = true
DNS_RESOLUTION_ENABLED = true
HXI_DIR = hxi
DB_PATH = mainnetdb
DB_LOG_PATH = mainnet.log
DB_CACHE_SIZE = 100_000
ROCKS_DB = rocksdb
REVALIDATE = false
RESCAN_DB = false
P_REPLY_RANDOM_TIP = 0.66
P_DROP_TRANSACTION = 0.0
P_SELECT_MILESTONE_CHILD = 0.7
P_SEND_MILESTONE = 0.02
P_PROPAGATE_REQUEST = 0.01
MWM = 1
PACKET_SIZE = 1200
REQ_HASH_SIZE = 32
QUEUE_SIZE = 1_000
P_DROP_CACHE_ENTRY = 0.02
CACHE_SIZE_BYTES = 150_000
ZMQ_THREADS = 1
ZMQ_IPC = ipc://sbx/feeds/{}
ZMQ_ENABLED = false
ZMQ_PORT = {}
GRAPH_ENABLED = false
MAX_DEPTH = 15
ALPHA = 0.001
TIP_SOLIDIFIER_ENABLED = true
POW_THREADS = 0
COORDINATOR_ADDRESS = 6a8413edc634e948e3446806afde11b17e0e188faf80a59a8b1147a0600cc5db
LOCAL_SNAPSHOTS_ENABLED = true
LOCAL_SNAPSHOTS_PRUNING_ENABLED = true
LOCAL_SNAPSHOTS_PRUNING_DELAY = 50000
LOCAL_SNAPSHOTS_INTERVAL_SYNCED = 10
LOCAL_SNAPSHOTS_INTERVAL_UNSYNCED = 1000
LOCAL_SNAPSHOTS_BASE_PATH = mainnet
LOCAL_SNAPSHOTS_DEPTH = 100
SNAPSHOT_FILE = /snapshotMainnet.txt
SNAPSHOT_SIG_FILE = /snapshotMainnet.sig
PREVIOUS_EPOCHS_SPENT_ADDRESSES_TXT = /previousEpochsSpentAddresses.txt
PREVIOUS_EPOCHS_SPENT_ADDRESSES_SIG = /previousEpochsSpentAddresses.sig
GLOBAL_SNAPSHOT_TIME = 1522235533
MILESTONE_START_INDEX = 0
NUM_KEYS_IN_MILESTONE = 10
MAX_ANALYZED_TXS = 20_000
SAVELOG = true
SAVELOG_BASE_PATH = logs/
SAVELOG_XML_FILE = /logback-save.xml
""".format(i, apiPortStart,udpReceiverPortStart,tcpReceiverPortStart,zmqIpcStart, zmqPortStart)
        nodeNeighbors = ""
        for n in topology.get_neighbor(i):
            nodeNeighbors = nodeNeighbors + "tcp://{}:{},".format(inet, tcpInetPort+n)
        nodeNeighbors = nodeNeighbors[:-1]
        config = config.replace("NEIGHBORS", "NEIGHBORS = {}".format(nodeNeighbors))
        apiPortStart +=1
        udpReceiverPortStart +=1
        tcpReceiverPortStart +=1
        zmqIpcStart +=1
        zmqPortStart +=1
        numNodes +=1
        if os.path.isdir(os.path.normpath("./many_configs")):
            pass
        else:
            os.mkdir(os.path.normpath("./many_configs"))
        with open(os.path.normpath("./many_configs/config{}.ini".format(i)), "w") as f:
            f.write(config)
