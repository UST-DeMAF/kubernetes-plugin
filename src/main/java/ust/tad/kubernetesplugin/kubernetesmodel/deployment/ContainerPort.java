package ust.tad.kubernetesplugin.kubernetesmodel.deployment;

import java.util.Objects;

public class ContainerPort {
    
    private String name;

    private int port;


    public ContainerPort() {
    }

    public ContainerPort(String name, int port) {
        this.name = name;
        this.port = port;
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

    public ContainerPort name(String name) {
        setName(name);
        return this;
    }

    public ContainerPort port(int port) {
        setPort(port);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof ContainerPort)) {
            return false;
        }
        ContainerPort containerPort = (ContainerPort) o;
        return Objects.equals(name, containerPort.name) && port == containerPort.port;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, port);
    }

    @Override
    public String toString() {
        return "{" +
            " name='" + getName() + "'" +
            ", port='" + getPort() + "'" +
            "}";
    }


}
