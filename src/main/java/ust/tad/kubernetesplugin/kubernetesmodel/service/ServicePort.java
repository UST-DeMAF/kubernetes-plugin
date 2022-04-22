package ust.tad.kubernetesplugin.kubernetesmodel.service;

import java.util.Objects;

public class ServicePort {
    
    private String name;

    private int port;

    private String targetPort;


    public ServicePort() {
    }

    public ServicePort(String name, int port, String targetPort) {
        this.name = name;
        this.port = port;
        this.targetPort = targetPort;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPort() {
        return this.port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getTargetPort() {
        return this.targetPort;
    }

    public void setTargetPort(String targetPort) {
        this.targetPort = targetPort;
    }

    public ServicePort name(String name) {
        setName(name);
        return this;
    }

    public ServicePort port(int port) {
        setPort(port);
        return this;
    }

    public ServicePort targetPort(String targetPort) {
        setTargetPort(targetPort);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof ServicePort)) {
            return false;
        }
        ServicePort servicePort = (ServicePort) o;
        return Objects.equals(name, servicePort.name) && port == servicePort.port && targetPort == servicePort.targetPort;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, port, targetPort);
    }

    @Override
    public String toString() {
        return "{" +
            " name='" + getName() + "'" +
            ", port='" + getPort() + "'" +
            ", targetPort='" + getTargetPort() + "'" +
            "}";
    }

    
}
