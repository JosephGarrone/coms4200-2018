#!/usr/bin/python

from mininet.topo import Topo
from mininet.net import Mininet
from mininet.util import dumpNodeConnections
from mininet.log import setLogLevel
from mininet.node import RemoteController
from mininet.cli import CLI
from mininet.node import CPULimitedHost
from mininet.link import TCLink

class Topology(Topo):
    """
    Represents a network topology
    """
    
    def build(self, depth=1, fanout=2):
        """
        Builds a tree topology
        :param depth: Depth of the tree
        :param fanout: Number of child nodes per element in tree
        :return:
        """
        # Numbering:  h1..N, s1..M
        self.hostNum = 1
        self.switchNum = 1
        # Build topology
        self.addTree(depth, fanout)

    def addTree(self, depth, fanout):
        """
        Recursively constructs a tree with the specified depth and fanout
        :param depth: Depth of the tree
        :param fanout: Number of child nodes per element in tree
        :return:
        """

        isSwitch = depth > 0
        if isSwitch:
            node = self.addSwitch('s%s' % self.switchNum)
            self.switchNum += 1
            for _ in range(fanout):
                child = self.addTree(depth - 1, fanout)
                # Link switch to host, bandwidth = 10Mbps, delay = 5ms,  loss = 2%, packet queue = 1000, Hierarchical Token Bucket turned on (whatever that is...)
                self.addLink(node, child, bw=10, delay='5ms', loss=2, max_queue_size=1000, use_htb=True)
        else:
            # Add a host with name 'h<n>' and give it an equal share of CPU usage
            node = self.addHost('h%s' % self.hostNum, cpu=.5/(fanout**depth))
            self.hostNum += 1
        return node

def test():
    """
    Creates the network and runs tests
    """
    topo = Topology(2, 3)
    net = Mininet(topo, controller=RemoteController('c0'), host=CPULimitedHost, link=TCLink)
    net.start()

    print "Dumping host connections"
    dumpNodeConnections(net.hosts)

    print "Running performance test"
    ## Below commands don't quite work....
    #h1, h7 = net.get('h1', 'h7')
    #net.iperf((h1, h7))

    CLI(net)
    net.stop()

if __name__ == '__main__':
    # Tell mininet to print useful information
    setLogLevel('info')
    test()