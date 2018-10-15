package org.coms4200.app;

import org.onosproject.net.Device;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import static java.lang.System.currentTimeMillis;

public class PortStatisticsReaderTask {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private boolean exit = false;
    private long delay;
    private int port;
    private int deviceIndex;
    private PortStatistics portStatistics;
    private Device device;
    private Timer timer = new Timer();
    protected DeviceService deviceService;

    public PortStatisticsReaderTask() {
    }

    public boolean isExit() {
        return exit;
    }

    public void setExit(boolean exit) {
        this.exit = exit;
    }

    public void setDelay(long delay) {
        this.delay = delay;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    public PortStatistics getPortStatistics() {
        return portStatistics;
    }

    public void setPortStatistics(PortStatistics portStatistics) {
        this.portStatistics = portStatistics;
    }

    public void setDeviceService(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    public void setDevice(Device device) {
        this.device = device;
    }

    public Timer getTimer() {
        return timer;
    }

    public void setTimer(Timer timer) {
        this.timer = timer;
    }

    public void schedule() {
        this.getTimer().schedule(new Task(), 0, 1000);
    }

    public int getDeviceIndex() {
        return deviceIndex;
    }

    public void setDeviceIndex(int deviceIndex) {
        this.deviceIndex = deviceIndex;
    }

    class Task extends TimerTask {
        public Device getDevice() {
            return device;
        }

        public DeviceService getDeviceService() {
            return deviceService;
        }

        public long getDelay() {
            return delay;
        }

        @Override
        public void run() {
            boolean secondRun = false;
            long lastMillis = 0;
            long currentMillis = 0;

            while (!isExit()) {
                List<PortStatistics> portStatistics = getDeviceService().getPortDeltaStatistics(getDevice().id());

                currentMillis = currentTimeMillis();

                for (PortStatistics stats : portStatistics) {
                    if (stats.port() == getPort()) {

                        if (secondRun) {
                            CalculatedPortStatistics statistics = new CalculatedPortStatistics(stats, currentMillis - lastMillis);
                            statistics.pushToElastic();
                            //log.info("Port Statistics\nDevice: " + device.id() + "\n" + statistics.toString("\t"));
                        } else {
                            secondRun = true;
                        }

                        lastMillis = currentMillis;

                        try {
                            Thread.sleep(getDelay() * 1000);
                            break;
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    enum Unit {
        BYTES (1.0d),
        KILOBYTES (1024.0d),
        MEGABYTES (1024.0d * 1024.0d),
        GIGABYTES (1024.0d * 1024.0d * 1024.0d),
        TERABYTES (1024.0d * 1024.0d * 1024.0d * 1024.0d);

        private double value;

        Unit(double value) {
            this.value = value;
        }

        public double getValue() {
            return value;
        }

        public String getSuffix() {
            switch (this) {
                case BYTES:
                    return "B/s";
                case KILOBYTES:
                    return "KB/s";
                case MEGABYTES:
                    return "MB/s";
                case GIGABYTES:
                    return "GB/s";
                case TERABYTES:
                    return "TB/s";
                default:
                    return "Unknown/s";
            }
        }
    }

    class CalculatedPortStatistics {
        private PortStatistics statistics;
        private Unit defaultUnits = Unit.MEGABYTES;
        private double timeDifferenceMillis;

        public CalculatedPortStatistics(PortStatistics statistics, double timeDifferenceMillis) {
            this.statistics = statistics;
            this.timeDifferenceMillis = timeDifferenceMillis;
        }

        public CalculatedPortStatistics(PortStatistics statistics, double timeDifferenceMillis, Unit defaultUnits) {
            this(statistics, timeDifferenceMillis);
            this.defaultUnits = defaultUnits;
        }

        public void setUnits(Unit defaultUnits) {
            this.defaultUnits = defaultUnits;
        }

        private double getElapsedSeconds() {
            if (statistics.durationSec() == 0) {
                return timeDifferenceMillis / 1000.0d;
            }

            return statistics.durationSec();
        }

        public double getByteReceiveRate(Unit units) {
            return (statistics.bytesReceived() / units.getValue()) / getElapsedSeconds();
        }

        public double getByteReceiveRate() {
            return getByteReceiveRate(defaultUnits);
        }

        public double getByteSendRate(Unit units) {
            return (statistics.bytesSent() / units.getValue()) / getElapsedSeconds();
        }

        public double getByteSendRate() {
            return getByteSendRate(defaultUnits);
        }

        public double getPacketReceiveRate() {
            return statistics.packetsReceived() / getElapsedSeconds();
        }

        public double getPacketSendRate() {
            return statistics.packetsSent() / getElapsedSeconds();
        }

        public double getPacketRxDropRate() {
            return statistics.packetsRxDropped() / getElapsedSeconds();
        }

        public double getPacketTxDropRate() {
            return statistics.packetsTxDropped() / getElapsedSeconds();
        }

        public double getPacketRxErrorRate() {
            return statistics.packetsRxErrors() / getElapsedSeconds();
        }

        public double getPacketTxErrorRate() {
            return statistics.packetsTxErrors() / getElapsedSeconds();
        }

        public String toJson() {
            StringBuilder builder = new StringBuilder();
            builder.append("{");
            builder.append("\"timestamp\":\"" + Instant.now() + "\",");
            builder.append("\"device\":\"" + device.id() + "\",");
            builder.append("\"device_index\":" + getDeviceIndex() + ",");
            builder.append("\"port\":" + port + ",");
            builder.append("\"time_delta\":" + getElapsedSeconds() + ",");
            builder.append("\"bytes_received\":" + statistics.bytesReceived() + ",");
            builder.append("\"bytes_sent\":" + statistics.bytesSent() + ",");
            builder.append("\"byte_receive_rate\":" + getByteReceiveRate() + ",");
            builder.append("\"byte_send_rate\":" + getByteSendRate() + ",");
            builder.append("\"packets_received\":" + statistics.packetsReceived() + ",");
            builder.append("\"packets_sent\":" + statistics.packetsSent() + ",");
            builder.append("\"packet_receive_rate\":" + getPacketReceiveRate() + ",");
            builder.append("\"packet_send_rate\":" + getPacketSendRate() + ",");
            builder.append("\"packet_rx_dropped\":" + statistics.packetsRxDropped() + ",");
            builder.append("\"packet_tx_dropped\":" + statistics.packetsTxDropped() + ",");
            builder.append("\"packet_rx_drop_rate\":" + getPacketRxDropRate() + ",");
            builder.append("\"packet_tx_drop_rate\":" + getPacketTxDropRate() + ",");
            builder.append("\"packet_rx_errors\":" + statistics.packetsRxErrors() + ",");
            builder.append("\"packet_tx_errors\":" + statistics.packetsTxErrors() + ",");
            builder.append("\"packet_rx_error_rate\":" + getPacketRxErrorRate() + ",");
            builder.append("\"packet_tx_error_rate\":" + getPacketTxErrorRate());
            builder.append("}");
            return builder.toString();
        }

        public String toString(String indent) {
            StringBuilder builder = new StringBuilder();
            builder.append(indent + "Port: " + statistics.port() + "\n");
            builder.append(indent + "Measured Time Delta: " + timeDifferenceMillis + " ms\n");
            builder.append(indent + "Reported Time Delta: " + statistics.durationSec() + " s (" + statistics.durationNano() + " ns)\n");
            builder.append(indent + "Used Time Delta: " + getElapsedSeconds() + " s\n");
            builder.append(indent + "Bytes Received: " + statistics.bytesReceived() + " bytes\n");
            builder.append(indent + "Bytes Sent: " + statistics.bytesSent() + " bytes\n");
            builder.append(indent + "Byte Receive Rate: " + getByteReceiveRate() + " " + defaultUnits.getSuffix() + "\n");
            builder.append(indent + "Byte Send Rate: " + getByteSendRate() + " " + defaultUnits.getSuffix() + "\n");
            builder.append(indent + "Packets Received: " + statistics.packetsReceived() + " PKTS\n");
            builder.append(indent + "Packets Sent: " + statistics.packetsSent() + " PKTS\n");
            builder.append(indent + "Packet Receive Rate: " + getPacketReceiveRate() + " PKTS/s\n");
            builder.append(indent + "Packet Send Rate: " + getPacketSendRate() + " PKTS/s\n");
            builder.append(indent + "Packet Rx Dropped: " + statistics.packetsRxDropped() + " PKTS\n");
            builder.append(indent + "Packet Tx Dropped: " + statistics.packetsTxDropped() + " PKTS\n");
            builder.append(indent + "Packet Rx Drop Rate: " + getPacketRxDropRate() + " PKTS/s\n");
            builder.append(indent + "Packet Tx Drop Rate: " + getPacketTxDropRate() + " PKTS/s\n");
            builder.append(indent + "Packet Rx Errors: " + statistics.packetsRxErrors() + " ERR/s\n");
            builder.append(indent + "Packet Tx Errors: " + statistics.packetsTxErrors() + " ERR/s\n");
            builder.append(indent + "Packet Rx Error Rate: " + getPacketRxErrorRate() + " ERR/s\n");
            builder.append(indent + "Packet Rx Error Rate: " + getPacketTxErrorRate() + " ERR/s\n");
            return builder.toString();
        }

        @Override
        public String toString() {
            return toString("");
        }

        public void pushToElastic() {
            ElasticRequest request = new ElasticRequest("http://localhost:9200/stats/port/", toJson(), "POST");
            String response = request.execute();
        }
    }
}