package ust.tad.kubernetesplugin.kubernetesmodel.deployment;

import java.util.Objects;

public class EnvironmentVariable {
    
    private String key;

    private String value;


    public EnvironmentVariable() {
    }

    public EnvironmentVariable(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return this.key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public EnvironmentVariable key(String key) {
        setKey(key);
        return this;
    }

    public EnvironmentVariable value(String value) {
        setValue(value);
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof EnvironmentVariable)) {
            return false;
        }
        EnvironmentVariable environmentVariable = (EnvironmentVariable) o;
        return Objects.equals(key, environmentVariable.key) && Objects.equals(value, environmentVariable.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return "{" +
            " key='" + getKey() + "'" +
            ", value='" + getValue() + "'" +
            "}";
    }

    
}
