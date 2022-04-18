package ust.tad.kubernetesplugin.kubernetesmodel.deployment;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Container {
    
    private String name;

    private String image;

    private Set<ContainerPort> containerPorts = new HashSet<>();

    private Set<EnvironmentVariable> environmentVariables = new HashSet<>();


    public Container() {
    }

    public Container(String name, String image, Set<ContainerPort> containerPorts, Set<EnvironmentVariable> environmentVariables) {
        this.name = name;
        this.image = image;
        this.containerPorts = containerPorts;
        this.environmentVariables = environmentVariables;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImage() {
        return this.image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public Set<ContainerPort> getContainerPorts() {
        return this.containerPorts;
    }

    public void setContainerPorts(Set<ContainerPort> containerPorts) {
        this.containerPorts = containerPorts;
    }

    public Set<EnvironmentVariable> getEnvironmentVariables() {
        return this.environmentVariables;
    }

    public void setEnvironmentVariables(Set<EnvironmentVariable> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    public Container name(String name) {
        setName(name);
        return this;
    }

    public Container image(String image) {
        setImage(image);
        return this;
    }

    public Container containerPorts(Set<ContainerPort> containerPorts) {
        setContainerPorts(containerPorts);
        return this;
    }

    public Container environmentVariables(Set<EnvironmentVariable> environmentVariables) {
        setEnvironmentVariables(environmentVariables);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Container)) {
            return false;
        }
        Container container = (Container) o;
        return Objects.equals(name, container.name) && Objects.equals(image, container.image) && Objects.equals(containerPorts, container.containerPorts) && Objects.equals(environmentVariables, container.environmentVariables);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, image, containerPorts, environmentVariables);
    }

    @Override
    public String toString() {
        return "{" +
            " name='" + getName() + "'" +
            ", image='" + getImage() + "'" +
            ", containerPorts='" + getContainerPorts() + "'" +
            ", environmentVariables='" + getEnvironmentVariables() + "'" +
            "}";
    }


}
