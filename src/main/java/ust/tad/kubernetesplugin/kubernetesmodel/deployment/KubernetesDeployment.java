package ust.tad.kubernetesplugin.kubernetesmodel.deployment;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class KubernetesDeployment {
    
    private String name;

    private int replicas;

    private Set<Label> labels = new HashSet<>();

    private Set<Container> container = new HashSet<>();


    public KubernetesDeployment() {
    }

    public KubernetesDeployment(String name, int replicas, Set<Label> labels, Set<Container> container) {
        this.name = name;
        this.replicas = replicas;
        this.labels = labels;
        this.container = container;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getReplicas() {
        return this.replicas;
    }

    public void setReplicas(int replicas) {
        this.replicas = replicas;
    }

    public Set<Label> getLabels() {
        return this.labels;
    }

    public void setLabels(Set<Label> labels) {
        this.labels = labels;
    }

    public Set<Container> getContainer() {
        return this.container;
    }

    public void setContainer(Set<Container> container) {
        this.container = container;
    }

    public KubernetesDeployment name(String name) {
        setName(name);
        return this;
    }

    public KubernetesDeployment replicas(int replicas) {
        setReplicas(replicas);
        return this;
    }

    public KubernetesDeployment labels(Set<Label> labels) {
        setLabels(labels);
        return this;
    }

    public KubernetesDeployment container(Set<Container> container) {
        setContainer(container);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof KubernetesDeployment)) {
            return false;
        }
        KubernetesDeployment deployment = (KubernetesDeployment) o;
        return Objects.equals(name, deployment.name) && replicas == deployment.replicas && Objects.equals(labels, deployment.labels) && Objects.equals(container, deployment.container);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, replicas, labels, container);
    }

    @Override
    public String toString() {
        return "{" +
            " name='" + getName() + "'" +
            ", replicas='" + getReplicas() + "'" +
            ", labels='" + getLabels() + "'" +
            ", container='" + getContainer() + "'" +
            "}";
    }


}
