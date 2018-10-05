# COMS4200 Team Project

This project simulates a network via Mininet and forwards OpenFlow data to Elasticsearch/Logstash/Kibana for visualisation.

## Getting Started

1. Download the COMS4200.ova file:
    ```bash
    scp sxxxxxxx@moss.labs.eait.uq.edu.au:/home/material/courses/COMS4200/COMS4200.ova . 
    ```
2. Import the image to VirtualBox (Suggest changing to 4 cores / 16gb of RAM if available)
3. In the network adapters settings for the VM, set the adapter as Bridged (So you can access all the ports from your main desktop)
4. Start the VM, login as `sdn/sdn` and run `ifconfig` to grab the IP address on your local network
5. Shutdown the VM and start it up in headless mode.
6. SSH to the VM with the `sdn/sdn` user account
    ```bash
    ssh sdn@<ip-address>
    ```
7. Confirm that `onos` works (Leave running):
    ```bash
    cd ~/onos
    onos-buck run onos-local -- clean
    ```
8. From a new terminal (See #6), confirm that `mininet` works (Leave running):
    ```bash
    sudo mn –-topo tree,3,2 --mac –-controller remote
    ```
    A bunch of messages should stream through on the `onos` terminal.
9. Confirm you can access the `onos` GUI by navigating to and logging in with `onos/rocks`:
    ```bash
    http://<ip-address>:8181/onos/ui
    ```
10. Install `docker` on the VM:
    ```bash
    sudo apt-get update

    sudo apt-get install \
        apt-transport-https \
        ca-certificates \
        curl \
        software-properties-common

    curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo apt-key add -

    sudo apt-key fingerprint 0EBFCD88

    sudo add-apt-repository \
        "deb [arch=amd64] https://download.docker.com/linux/ubuntu \
        $(lsb_release -cs) \
        stable"

    sudo apt-get update

    sudo apt-get install docker-ce
    ```
11. Install `docker-compose` on the VM:
    ```bash
    sudo curl -L "https://github.com/docker/compose/releases/download/1.22.0/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose

    sudo chmod +x /usr/local/bin/docker-compose
    ```
12. Install `docker-elk` on the VM:
    ```bash
    git clone https://github.com/deviantony/docker-elk

    cd docker-elk

    sudo docker-compose up -d
    ```
13. Clone this repository and from the top level run:
    ```bash
    maven clean install
    ```
14. Install the application (Whilst running `onos`):
    ```bash
    onos-app localhost install path/to/coms4200-1.10.0.oar
    ```
15. Activate the application and de-activate conflicting ones:
    ```bash
    onos> app activate org.coms4200.app
    onos> app deactivate org.onosproject.fwd
    onos> app deactivate org.onosproject.proxyarp
    ```
16. Access Kibana at:
    ```bash
    http://<ip-address>:5601/app/kibana
    ```

## Running everything after initial install

1. Open 4 terminals on the VM
2. In terminal #1 (Onos)
    ```bash
    cd ~/onos && onos-buck run onos-local -- clean
    ```
3. In terminal #2 (Mininet CLI)
    ```bash
    sudo mn --topo single,3 --mac --controller remote
    # Or other applicable `mn` command
    ```
4. In terminal #3 (Onos CLI)
    ```bash
    onos localhost
    ```
5. In terminal #4 (General purpose)
    ```bash
    onos localhost install path/to/coms4200-1.10.0.oar
    ```
    _or (to reinstall and immediately activate)_
    ```bash
    onos localhost reinstall! path/to/coms4200-1.10.0.oar
    ```