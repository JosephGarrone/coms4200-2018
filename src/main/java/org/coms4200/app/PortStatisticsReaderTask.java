package org.coms4200.app;

import org.onosproject.net.Device;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.PortStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class PortStatisticsReaderTask {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private boolean exit = false;
    private long delay;
    private int port;
    private PortStatistics portStatistics;
    private Device device;
    private Timer timer = new Timer();
    protected DeviceService deviceService;

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
            while (!isExit()) {
                List<PortStatistics> portStatistics = getDeviceService().getPortDeltaStatistics(getDevice().id());

                for (PortStatistics stats : portStatistics) {
                    if (stats.port() == getPort()) {
                        double rate = (stats.bytesReceived() / (1024.0 * 1024.0));
                        log.info("Port " + port + " Rate " + rate + " MB/s");

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
}