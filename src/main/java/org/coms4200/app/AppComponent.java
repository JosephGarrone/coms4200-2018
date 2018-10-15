package org.coms4200.app;
import org.apache.felix.scr.annotations.*;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.action.admin.indices.create.CreateIndexRequest;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.onosproject.net.flow.*;
import org.onosproject.net.packet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Skeletal ONOS application component.
 */
@Component(immediate = true)
public class AppComponent {
    private final Logger log = LoggerFactory.getLogger(getClass());

    // 1#-------> You need to refer to the interface class in order register your component
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    // In order to add/delete matching rules of the selector
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    // PacketProcessor pktprocess; // To process incoming packets and use methods
    // of PacketProcess such as addProcessor and removeProcessor
    PacketProcessor pktprocess = new LearningSwitch();
    private ApplicationId appId;
    private PortNumber in_port, out_port;
    Map<MacAddress, PortNumber> mytable = new HashMap<MacAddress, PortNumber>();
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    private Map<Integer, PortStatisticsReaderTask> statTasks = new HashMap<>();

    private CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
    private RestHighLevelClient client;

    @Activate
    protected void activate() {
        log.info("Starting");

        appId = coreService.registerApplication("org.coms4200.app");
        packetService.addProcessor(pktprocess, PacketProcessor.director(1));
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_ARP);

        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE,
                appId, Optional.empty());
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE,
                appId, Optional.empty());

        // Start ELK insertion session
        credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials("",""));
        client = new RestHighLevelClient(RestClient.builder()
                .setHttpClientConfigCallback(httpAsyncClientBuilder -> httpAsyncClientBuilder.setDefaultCredentialsProvider(credentialsProvider)));

        try {
            Response response = client.getLowLevelClient().performRequest("HEAD", "/ports");
            int statusCode = response.getStatusLine().getStatusCode();

            if (statusCode == 404) {
                CreateIndexRequest cir = new CreateIndexRequest("ports");
                CreateIndexResponse cirsp = client.indices().create(cir);
            }
        } catch (IOException ex) {
            // Do something? How about we just don't throw alright?
        }

        // Start port statistics monitoring
        Iterable<Device> devices = deviceService.getDevices();
        for (Device dev : devices) {
            List<Port> ports = deviceService.getPorts(dev.id());

            for (Port port : ports) {
                log.info("Fetching info for port " + port.number());
                PortStatistics stats1 = deviceService.getStatisticsForPort(dev.id(), port.number());
                PortStatistics stats2 = deviceService.getDeltaStatisticsForPort(dev.id(), port.number());

                if (stats1 != null) {
                    PortStatisticsReaderTask task = new PortStatisticsReaderTask(client);
                    task.setDelay(1);
                    task.setExit(false);
                    task.setPort(stats1.port());
                    task.setDeviceService(deviceService);
                    task.setDevice(dev);
                    task.schedule();

                    statTasks.put(stats1.port(), task);
                }
            }
        }

        log.info("Started");
    }

    private class LearningSwitch implements PacketProcessor {
        @Override
        public void process(PacketContext pktIn) {
            InboundPacket pkt = pktIn.inPacket();
            // Get the following information from incoming pkt
            MacAddress DstMac = pkt.parsed().getDestinationMAC();
            MacAddress SrcMac = pkt.parsed().getSourceMAC();
            DeviceId switchId = pkt.receivedFrom().deviceId();
            // Learn the MAC address of the sender
            in_port = pkt.receivedFrom().port();
            // Store this information to learn for next time
            mytable.put(SrcMac, in_port);
            // Check if the MAC address exists in the table or not. If yes,
            // get the port and send the packet directly. If not, flood
            if (mytable.containsKey(DstMac)) {
                out_port = (PortNumber)mytable.get(DstMac);
            }
            else {
                out_port = PortNumber.FLOOD;
                pktIn.treatmentBuilder().setOutput(out_port);
                pktIn.send();
            }
            if (out_port!=PortNumber.FLOOD){
                pktIn.treatmentBuilder().setOutput(out_port);
                pktIn.send();
                // Install forwarding rules to avoid Pkt-in next time
                TrafficSelector.Builder selector =
                        DefaultTrafficSelector.builder();
                selector.matchEthDst(DstMac)
                        .matchEthSrc(SrcMac);
                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                        .setOutput(out_port)
                        .build();
                FlowRule flowRule = DefaultFlowRule.builder()
                        .forDevice(switchId)
                        .withSelector(selector.build())
                        .withTreatment(treatment)
                        .withPriority(50000)
                        .fromApp(appId)
                        .makePermanent()
                        .build();
                flowRuleService.applyFlowRules(flowRule);
            }
        }
    }

    @Deactivate
    protected void deactivate() {
        log.info("Stopped");
        packetService.removeProcessor(pktprocess);
        flowRuleService.removeFlowRulesById(appId);

        for (PortStatisticsReaderTask task : statTasks.values()) {
            task.setExit(true);
            task.getTimer().cancel();
        }

        try {
            client.close();
        } catch (IOException ex) {
            // Yeet that outta here
        }

        withdrawIntercepts();
    }

    private void withdrawIntercepts() {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE,
                appId);
        selector.matchEthType(Ethernet.TYPE_ARP);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE,
                appId);
    }
}