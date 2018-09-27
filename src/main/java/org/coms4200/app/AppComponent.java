package org.coms4200.app;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onosproject.net.Device;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.packet.OutboundPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//#################### Imported those ##################################
// In order to use reference and deal with the core service
// For step # 1
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onosproject.core.CoreService;
import org.onosproject.net.packet.PacketService;
import org.onosproject.core.ApplicationId;
// In order to register at for the pkt-in event
// For step # 2
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketContext;
// In order you install matching rules (traffic selector)
// For step # 3
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.FlowRuleService;
// In order to get access to packet header
// For step # 4
import org.onlab.packet.Ethernet;
import org.onosproject.net.packet.PacketPriority;
import java.util.Optional;
// In order to access the Pkt-In header
//For step #5
import org.onosproject.net.PortNumber;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.ARP;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.DeviceId;
import org.onosproject.net.ConnectPoint;
import java.util.Map;
import java.util.HashMap;
//#####################################################################
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
    //#############################
    @Activate
    protected void activate() {
        // 2#-------> You need to register your component at the core
        appId = coreService.registerApplication("org.coms4200.app");
        // 3#-------> This is to add a listener for the pkt-in event with priority and process incoming pkts
        packetService.addProcessor(pktprocess, PacketProcessor.director(1));
        // 4#-------> This is to add matching rules on incoming packets
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        // For example, here we just want ARP and IPv4 packets to be forwarded to this app
        selector.matchEthType(Ethernet.TYPE_ARP);

        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE,
                appId, Optional.empty());
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE,
                appId, Optional.empty());
        log.info("Started");
    }
    // 5#-------> Override the packeProcessor class in order to change whatever methods you like
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
        // 6#-------> You should add this removal to delete the listener
        // and delete the rules once the you deactivate the app
        log.info("Stopped");
        packetService.removeProcessor(pktprocess);
        flowRuleService.removeFlowRulesById(appId);
        withdrawIntercepts();
    }
    // 7#---------> You need to cancel the all selectors
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